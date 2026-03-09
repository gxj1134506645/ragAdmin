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
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DocumentParseProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseProcessor.class);

    private final DocumentParseTaskMapper documentParseTaskMapper;
    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final ChunkMapper chunkMapper;
    private final MinioClientFactory minioClientFactory;

    public DocumentParseProcessor(
            DocumentParseTaskMapper documentParseTaskMapper,
            DocumentMapper documentMapper,
            DocumentVersionMapper documentVersionMapper,
            ChunkMapper chunkMapper,
            MinioClientFactory minioClientFactory
    ) {
        this.documentParseTaskMapper = documentParseTaskMapper;
        this.documentMapper = documentMapper;
        this.documentVersionMapper = documentVersionMapper;
        this.chunkMapper = chunkMapper;
        this.minioClientFactory = minioClientFactory;
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
        String docType = document.getDocType() == null ? "" : document.getDocType().toUpperCase(Locale.ROOT);
        if (!List.of("TXT", "MD", "MARKDOWN").contains(docType)) {
            throw new IllegalArgumentException("当前仅支持 TXT/MD 文档解析");
        }

        MinioClient minioClient = minioClientFactory.createClient();
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(version.getStorageBucket())
                .object(version.getStorageObjectKey())
                .build())) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return new ParsedContent(splitIntoChunks(content));
        }
    }

    private List<String> splitIntoChunks(String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int maxLength = 500;
        int overlap = 100;
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxLength, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
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
