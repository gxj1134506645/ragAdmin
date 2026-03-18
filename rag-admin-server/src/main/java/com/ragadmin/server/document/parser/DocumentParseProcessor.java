package com.ragadmin.server.document.parser;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.mapper.DocumentVersionMapper;
import com.ragadmin.server.task.entity.TaskStepRecordEntity;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import com.ragadmin.server.document.support.ChunkVectorizationService;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentParseProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseProcessor.class);
    private static final Duration STALE_RUNNING_TIMEOUT = Duration.ofMinutes(5);
    private static final String STALE_TASK_MESSAGE = "任务执行中断，系统已自动标记失败，请重试";

    private final DocumentParseTaskMapper documentParseTaskMapper;
    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final ChunkMapper chunkMapper;
    private final DocumentContentExtractor documentContentExtractor;
    private final TaskStepRecordMapper taskStepRecordMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChunkVectorizationService chunkVectorizationService;
    private final TransactionTemplate transactionTemplate;

    public DocumentParseProcessor(
            DocumentParseTaskMapper documentParseTaskMapper,
            DocumentMapper documentMapper,
            DocumentVersionMapper documentVersionMapper,
            ChunkMapper chunkMapper,
            DocumentContentExtractor documentContentExtractor,
            TaskStepRecordMapper taskStepRecordMapper,
            KnowledgeBaseService knowledgeBaseService,
            ChunkVectorizationService chunkVectorizationService,
            PlatformTransactionManager transactionManager
    ) {
        this.documentParseTaskMapper = documentParseTaskMapper;
        this.documentMapper = documentMapper;
        this.documentVersionMapper = documentVersionMapper;
        this.chunkMapper = chunkMapper;
        this.documentContentExtractor = documentContentExtractor;
        this.taskStepRecordMapper = taskStepRecordMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.chunkVectorizationService = chunkVectorizationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void processWaitingTasks() {
        recoverStaleRunningTasks();

        List<DocumentParseTaskEntity> tasks = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getTaskStatus, "WAITING")
                .orderByAsc(DocumentParseTaskEntity::getId)
                .last("LIMIT 3"));
        for (DocumentParseTaskEntity task : tasks) {
            processSingleTask(task.getId());
        }
    }

    public void recoverStaleRunningTasks() {
        LocalDateTime staleBefore = LocalDateTime.now().minus(STALE_RUNNING_TIMEOUT);
        List<DocumentParseTaskEntity> staleTasks = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getTaskStatus, "RUNNING")
                .lt(DocumentParseTaskEntity::getStartedAt, staleBefore)
                .orderByAsc(DocumentParseTaskEntity::getId)
                .last("LIMIT 20"));
        for (DocumentParseTaskEntity staleTask : staleTasks) {
            markFailed(staleTask.getId(), new IllegalStateException(STALE_TASK_MESSAGE));
            log.warn("检测到超时未完成的解析任务，已自动标记失败，taskId={}, startedAt={}",
                    staleTask.getId(), staleTask.getStartedAt());
        }
    }

    public void processSingleTask(Long taskId) {
        ProcessingContext context;
        try {
            context = markRunning(taskId);
        } catch (IllegalStateException ex) {
            // 调度轮询与多实例并发场景下，任务可能已被其他工作线程抢占，这里按已处理跳过，避免误标记失败。
            log.info("解析任务跳过执行，taskId={}, reason={}", taskId, ex.getMessage());
            return;
        }

        try {
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(context.document().getKbId());

            TaskStepRecordEntity extractStep = startStep(context.task().getId(), "EXTRACT_TEXT", "文本抽取");
            ParsedContent parsedContent = parseContent(context.document(), context.version());
            completeStep(extractStep);

            TaskStepRecordEntity chunkStep = startStep(context.task().getId(), "PERSIST_CHUNKS", "切片入库");
            List<ChunkEntity> chunks = persistChunks(context, parsedContent.chunks());
            completeStep(chunkStep);

            TaskStepRecordEntity embeddingStep = startStep(context.task().getId(), "GENERATE_EMBEDDING", "生成向量");
            chunkVectorizationService.vectorize(knowledgeBase, chunks);
            completeStep(embeddingStep);

            markSuccess(context);
            log.info("文档解析任务执行成功，taskId={}, documentId={}, chunkCount={}",
                    context.task().getId(), context.document().getId(), parsedContent.chunks().size());
        } catch (Exception ex) {
            markFailed(taskId, ex);
            log.error("文档解析任务执行失败，taskId={}", taskId, ex);
        }
    }

    protected ProcessingContext markRunning(Long taskId) {
        return transactionTemplate.execute(status -> {
            DocumentParseTaskEntity task = documentParseTaskMapper.selectById(taskId);
            if (task == null || !"WAITING".equals(task.getTaskStatus())) {
                throw new IllegalStateException("任务不存在或状态已变更");
            }
            DocumentEntity document = documentMapper.selectById(task.getDocumentId());
            DocumentVersionEntity version = documentVersionMapper.selectById(task.getDocumentVersionId());
            if (document == null || version == null) {
                throw new IllegalStateException("文档或文档版本不存在");
            }

            task.setTaskStatus("RUNNING");
            task.setStartedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            documentParseTaskMapper.updateById(task);

            document.setParseStatus("PROCESSING");
            documentMapper.updateById(document);
            version.setParseStatus("PROCESSING");
            version.setParseStartedAt(LocalDateTime.now());
            version.setParseFinishedAt(null);
            documentVersionMapper.updateById(version);

            return new ProcessingContext(task, document, version);
        });
    }

    protected TaskStepRecordEntity startStep(Long taskId, String stepCode, String stepName) {
        return transactionTemplate.execute(status -> {
            TaskStepRecordEntity step = new TaskStepRecordEntity();
            step.setTaskId(taskId);
            step.setStepCode(stepCode);
            step.setStepName(stepName);
            step.setStepStatus("RUNNING");
            step.setStartedAt(LocalDateTime.now());
            taskStepRecordMapper.insert(step);
            return step;
        });
    }

    protected void completeStep(TaskStepRecordEntity step) {
        transactionTemplate.executeWithoutResult(status -> {
            step.setStepStatus("SUCCESS");
            step.setFinishedAt(LocalDateTime.now());
            step.setErrorMessage(null);
            taskStepRecordMapper.updateById(step);
        });
    }

    protected List<ChunkEntity> persistChunks(ProcessingContext context, List<String> chunks) {
        return transactionTemplate.execute(status -> {
            List<ChunkEntity> existingChunks = chunkMapper.selectList(new LambdaQueryWrapper<ChunkEntity>()
                    .eq(ChunkEntity::getDocumentVersionId, context.version().getId())
                    .orderByAsc(ChunkEntity::getChunkNo));
            chunkVectorizationService.deleteRefsByChunkIds(existingChunks.stream().map(ChunkEntity::getId).toList());
            chunkMapper.delete(new LambdaQueryWrapper<ChunkEntity>()
                    .eq(ChunkEntity::getDocumentVersionId, context.version().getId()));

            int chunkNo = 1;
            List<ChunkEntity> persistedChunks = new ArrayList<>();
            for (String chunkText : chunks) {
                ChunkEntity entity = new ChunkEntity();
                entity.setKbId(context.document().getKbId());
                entity.setDocumentId(context.document().getId());
                entity.setDocumentVersionId(context.version().getId());
                entity.setChunkNo(chunkNo++);
                entity.setChunkText(chunkText);
                entity.setTokenCount(estimateTokenCount(chunkText));
                entity.setCharCount(chunkText.length());
                entity.setMetadataJson(null);
                entity.setEnabled(Boolean.TRUE);
                chunkMapper.insert(entity);
                persistedChunks.add(entity);
            }
            return persistedChunks;
        });
    }

    protected void markSuccess(ProcessingContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            DocumentParseTaskEntity task = context.task();
            DocumentEntity document = context.document();
            DocumentVersionEntity version = context.version();

            task.setTaskStatus("SUCCESS");
            task.setFinishedAt(now);
            task.setErrorMessage(null);
            documentParseTaskMapper.updateById(task);

            document.setParseStatus("SUCCESS");
            documentMapper.updateById(document);
            version.setParseStatus("SUCCESS");
            version.setParseFinishedAt(now);
            documentVersionMapper.updateById(version);
        });
    }

    protected void markFailed(Long taskId, Exception ex) {
        transactionTemplate.executeWithoutResult(status -> {
            DocumentParseTaskEntity task = documentParseTaskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            DocumentEntity document = documentMapper.selectById(task.getDocumentId());
            DocumentVersionEntity version = documentVersionMapper.selectById(task.getDocumentVersionId());
            LocalDateTime now = LocalDateTime.now();

            task.setTaskStatus("FAILED");
            task.setFinishedAt(now);
            task.setErrorMessage(buildErrorMessage(ex));
            documentParseTaskMapper.updateById(task);

            if (document != null) {
                document.setParseStatus("FAILED");
                documentMapper.updateById(document);
            }
            if (version != null) {
                version.setParseStatus("FAILED");
                version.setParseFinishedAt(now);
                documentVersionMapper.updateById(version);
            }

            TaskStepRecordEntity runningStep = taskStepRecordMapper.selectOne(new LambdaQueryWrapper<TaskStepRecordEntity>()
                    .eq(TaskStepRecordEntity::getTaskId, taskId)
                    .eq(TaskStepRecordEntity::getStepStatus, "RUNNING")
                    .orderByDesc(TaskStepRecordEntity::getId)
                    .last("LIMIT 1"));
            if (runningStep != null) {
                runningStep.setStepStatus("FAILED");
                runningStep.setFinishedAt(now);
                runningStep.setErrorMessage(buildErrorMessage(ex));
                taskStepRecordMapper.updateById(runningStep);
            }
        });
    }

    private ParsedContent parseContent(DocumentEntity document, DocumentVersionEntity version) throws Exception {
        String content = documentContentExtractor.extract(document, version);
        return new ParsedContent(splitIntoChunks(content));
    }

    private List<String> splitIntoChunks(String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        List<String> paragraphs = List.of(normalized.split("\\n\\s*\\n"));
        StringBuilder current = new StringBuilder();
        int chunkNo = 0;
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() > 0 && current.length() + trimmed.length() + 2 > 800) {
                chunks.add(current.toString());
                chunkNo++;
                current = new StringBuilder(overlapTail(chunks.get(chunkNo - 1), 120));
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        if (chunks.isEmpty()) {
            chunks.add(normalized.substring(0, Math.min(normalized.length(), 800)));
        }
        return chunks;
    }

    private String overlapTail(String source, int tailLength) {
        if (source.length() <= tailLength) {
            return source;
        }
        return source.substring(source.length() - tailLength);
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, text.length() / 4);
    }

    private String buildErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "解析失败";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record ProcessingContext(
            DocumentParseTaskEntity task,
            DocumentEntity document,
            DocumentVersionEntity version
    ) {
    }

    private record ParsedContent(List<String> chunks) {
    }
}
