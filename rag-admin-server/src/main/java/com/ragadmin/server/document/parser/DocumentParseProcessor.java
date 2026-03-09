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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentParseProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseProcessor.class);

    private final DocumentParseTaskMapper documentParseTaskMapper;
    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final ChunkMapper chunkMapper;
    private final DocumentContentExtractor documentContentExtractor;

    public DocumentParseProcessor(
            DocumentParseTaskMapper documentParseTaskMapper,
            DocumentMapper documentMapper,
            DocumentVersionMapper documentVersionMapper,
            ChunkMapper chunkMapper,
            DocumentContentExtractor documentContentExtractor
    ) {
        this.documentParseTaskMapper = documentParseTaskMapper;
        this.documentMapper = documentMapper;
        this.documentVersionMapper = documentVersionMapper;
        this.chunkMapper = chunkMapper;
        this.documentContentExtractor = documentContentExtractor;
    }

    public void processWaitingTasks() {
        List<DocumentParseTaskEntity> tasks = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getTaskStatus, "WAITING")
                .orderByAsc(DocumentParseTaskEntity::getId)
                .last("LIMIT 3"));
        for (DocumentParseTaskEntity task : tasks) {
            processSingleTask(task.getId());
        }
    }

    public void processSingleTask(Long taskId) {
        try {
            ProcessingContext context = markRunning(taskId);
            ParsedContent parsedContent = parseContent(context.document(), context.version());
            persistChunks(context, parsedContent.chunks());
            markSuccess(context);
            log.info("文档解析任务执行成功，taskId={}, documentId={}, chunkCount={}",
                    context.task().getId(), context.document().getId(), parsedContent.chunks().size());
        } catch (Exception ex) {
            markFailed(taskId, ex);
            log.error("文档解析任务执行失败，taskId={}", taskId, ex);
        }
    }

    @Transactional
    protected ProcessingContext markRunning(Long taskId) {
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
    }

    @Transactional
    protected void persistChunks(ProcessingContext context, List<String> chunks) {
        chunkMapper.delete(new LambdaQueryWrapper<ChunkEntity>()
                .eq(ChunkEntity::getDocumentVersionId, context.version().getId()));

        int chunkNo = 1;
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
        }
    }

    @Transactional
    protected void markSuccess(ProcessingContext context) {
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
    }

    @Transactional
    protected void markFailed(Long taskId, Exception ex) {
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
