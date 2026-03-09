package com.ragadmin.server.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.dto.ChunkResponse;
import com.ragadmin.server.document.dto.CreateDocumentRequest;
import com.ragadmin.server.document.dto.DocumentResponse;
import com.ragadmin.server.document.dto.ParseDocumentResponse;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.mapper.DocumentVersionMapper;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final DocumentParseTaskMapper documentParseTaskMapper;
    private final ChunkMapper chunkMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    public DocumentService(
            DocumentMapper documentMapper,
            DocumentVersionMapper documentVersionMapper,
            DocumentParseTaskMapper documentParseTaskMapper,
            ChunkMapper chunkMapper,
            KnowledgeBaseService knowledgeBaseService
    ) {
        this.documentMapper = documentMapper;
        this.documentVersionMapper = documentVersionMapper;
        this.documentParseTaskMapper = documentParseTaskMapper;
        this.chunkMapper = chunkMapper;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Transactional
    public DocumentResponse createDocument(Long kbId, CreateDocumentRequest request, Long operatorUserId) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(kbId);

        DocumentEntity document = new DocumentEntity();
        document.setKbId(knowledgeBase.getId());
        document.setDocName(request.getDocName());
        document.setDocType(request.getDocType());
        document.setStorageBucket(request.getStorageBucket());
        document.setStorageObjectKey(request.getStorageObjectKey());
        document.setCurrentVersion(1);
        document.setParseStatus("PENDING");
        document.setEnabled(Boolean.TRUE);
        document.setFileSize(request.getFileSize());
        document.setContentHash(request.getContentHash());
        document.setCreatedBy(operatorUserId);
        documentMapper.insert(document);

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setDocumentId(document.getId());
        version.setVersionNo(1);
        version.setStorageBucket(request.getStorageBucket());
        version.setStorageObjectKey(request.getStorageObjectKey());
        version.setContentHash(request.getContentHash());
        version.setParseStatus("PENDING");
        version.setCreatedBy(operatorUserId);
        documentVersionMapper.insert(version);

        return toResponse(document);
    }

    public DocumentResponse getDocument(Long documentId) {
        return toResponse(requireDocument(documentId));
    }

    public void updateDocumentStatus(Long documentId, Boolean enabled) {
        DocumentEntity document = requireDocument(documentId);
        // 文档启停只影响是否参与后续使用，不直接清理解析结果，便于后续重新启用。
        document.setEnabled(enabled);
        documentMapper.updateById(document);
    }

    public PageResponse<ChunkResponse> listChunks(Long documentId, long pageNo, long pageSize) {
        requireDocument(documentId);
        Page<ChunkEntity> page = chunkMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<ChunkEntity>()
                        .eq(ChunkEntity::getDocumentId, documentId)
                        .orderByAsc(ChunkEntity::getChunkNo)
        );
        return new PageResponse<>(
                page.getRecords().stream().map(this::toChunkResponse).toList(),
                pageNo,
                pageSize,
                page.getTotal()
        );
    }

    @Transactional
    public ParseDocumentResponse submitParseTask(Long documentId) {
        DocumentParseTaskEntity task = submitParseTask(documentId, 0);
        DocumentEntity document = documentMapper.selectById(documentId);
        return new ParseDocumentResponse(task.getId(), documentId, task.getDocumentVersionId(), task.getTaskStatus(), document.getParseStatus());
    }

    @Transactional
    public DocumentParseTaskEntity submitParseTask(Long documentId, int retryCount) {
        DocumentEntity document = requireDocument(documentId);

        DocumentVersionEntity version = documentVersionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getDocumentId, documentId)
                .eq(DocumentVersionEntity::getVersionNo, document.getCurrentVersion())
                .last("LIMIT 1"));
        if (version == null) {
            throw new BusinessException("DOCUMENT_VERSION_NOT_FOUND", "文档版本不存在", HttpStatus.NOT_FOUND);
        }

        DocumentParseTaskEntity existingTask = documentParseTaskMapper.selectOne(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getDocumentId, documentId)
                .eq(DocumentParseTaskEntity::getDocumentVersionId, version.getId())
                .in(DocumentParseTaskEntity::getTaskStatus, "WAITING", "RUNNING")
                .last("LIMIT 1"));
        if (existingTask != null) {
            return existingTask;
        }

        DocumentParseTaskEntity task = new DocumentParseTaskEntity();
        task.setKbId(document.getKbId());
        task.setDocumentId(documentId);
        task.setDocumentVersionId(version.getId());
        task.setTaskStatus("WAITING");
        task.setRetryCount(retryCount);
        documentParseTaskMapper.insert(task);

        document.setParseStatus("PENDING");
        documentMapper.updateById(document);
        version.setParseStatus("PENDING");
        documentVersionMapper.updateById(version);

        return task;
    }

    private DocumentResponse toResponse(DocumentEntity document) {
        return new DocumentResponse(
                document.getId(),
                document.getKbId(),
                document.getDocName(),
                document.getDocType(),
                document.getStorageBucket(),
                document.getStorageObjectKey(),
                document.getCurrentVersion(),
                document.getParseStatus(),
                document.getEnabled(),
                document.getFileSize(),
                document.getContentHash()
        );
    }

    private ChunkResponse toChunkResponse(ChunkEntity chunk) {
        return new ChunkResponse(
                chunk.getId(),
                chunk.getChunkNo(),
                chunk.getChunkText(),
                chunk.getTokenCount(),
                chunk.getCharCount(),
                chunk.getEnabled()
        );
    }

    private DocumentEntity requireDocument(Long documentId) {
        DocumentEntity document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("DOCUMENT_NOT_FOUND", "文档不存在", HttpStatus.NOT_FOUND);
        }
        return document;
    }
}
