package com.ragadmin.server.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.dto.ActivateDocumentVersionResponse;
import com.ragadmin.server.document.dto.ChunkResponse;
import com.ragadmin.server.document.dto.CreateDocumentRequest;
import com.ragadmin.server.document.dto.CreateDocumentVersionRequest;
import com.ragadmin.server.document.dto.DocumentResponse;
import com.ragadmin.server.document.dto.DocumentVersionResponse;
import com.ragadmin.server.document.dto.InternalTaskCompleteRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class DocumentService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentVersionMapper documentVersionMapper;

    @Autowired
    private DocumentParseTaskMapper documentParseTaskMapper;

    @Autowired
    private ChunkMapper chunkMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

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

    @Transactional
    public DocumentResponse createDocumentVersion(Long documentId, CreateDocumentVersionRequest request, Long operatorUserId) {
        DocumentEntity document = requireDocument(documentId);
        int newVersionNo = document.getCurrentVersion() + 1;

        // 新版本创建后立即切换 currentVersion，并把文档解析状态重置为 PENDING，后续由显式解析接口触发真正处理。
        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setDocumentId(document.getId());
        version.setVersionNo(newVersionNo);
        version.setStorageBucket(request.getStorageBucket());
        version.setStorageObjectKey(request.getStorageObjectKey());
        version.setContentHash(request.getContentHash());
        version.setParseStatus("PENDING");
        version.setCreatedBy(operatorUserId);
        documentVersionMapper.insert(version);

        document.setStorageBucket(request.getStorageBucket());
        document.setStorageObjectKey(request.getStorageObjectKey());
        document.setCurrentVersion(newVersionNo);
        document.setParseStatus("PENDING");
        document.setFileSize(request.getFileSize());
        document.setContentHash(request.getContentHash());
        documentMapper.updateById(document);
        return toResponse(document);
    }

    @Transactional
    public ActivateDocumentVersionResponse activateVersion(Long documentId, Long versionId) {
        DocumentEntity document = requireDocument(documentId);
        DocumentVersionEntity version = documentVersionMapper.selectById(versionId);
        if (version == null || !version.getDocumentId().equals(documentId)) {
            throw new BusinessException("DOCUMENT_VERSION_NOT_FOUND", "文档版本不存在", HttpStatus.NOT_FOUND);
        }

        // 激活历史版本时，主文档的存储信息和 currentVersion 必须同步回滚到目标版本。
        document.setStorageBucket(version.getStorageBucket());
        document.setStorageObjectKey(version.getStorageObjectKey());
        document.setCurrentVersion(version.getVersionNo());
        document.setContentHash(version.getContentHash());
        document.setParseStatus(version.getParseStatus());
        documentMapper.updateById(document);

        return new ActivateDocumentVersionResponse(
                document.getId(),
                version.getId(),
                document.getCurrentVersion(),
                document.getParseStatus()
        );
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

    public PageResponse<DocumentResponse> listKnowledgeBaseDocuments(
            Long kbId,
            String keyword,
            String parseStatus,
            Boolean enabled,
            long pageNo,
            long pageSize
    ) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(kbId);
        Page<DocumentEntity> page = documentMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<DocumentEntity>()
                        .eq(DocumentEntity::getKbId, knowledgeBase.getId())
                        .eq(StringUtils.hasText(parseStatus), DocumentEntity::getParseStatus, parseStatus)
                        .eq(enabled != null, DocumentEntity::getEnabled, enabled)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(DocumentEntity::getDocName, keyword)
                                .or()
                                .like(DocumentEntity::getDocType, keyword))
                        .orderByDesc(DocumentEntity::getId)
        );
        return new PageResponse<>(
                page.getRecords().stream().map(this::toResponse).toList(),
                pageNo,
                pageSize,
                page.getTotal()
        );
    }

    public PageResponse<DocumentVersionResponse> listVersions(Long documentId, long pageNo, long pageSize) {
        requireDocument(documentId);
        Page<DocumentVersionEntity> page = documentVersionMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<DocumentVersionEntity>()
                        .eq(DocumentVersionEntity::getDocumentId, documentId)
                        .orderByDesc(DocumentVersionEntity::getVersionNo)
        );
        return new PageResponse<>(
                page.getRecords().stream()
                        .map(item -> toVersionResponse(item, documentId))
                        .toList(),
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

        return submitParseTask(document, version, retryCount);
    }

    @Transactional
    public DocumentParseTaskEntity submitParseTask(Long documentId, Long versionId, int retryCount) {
        DocumentEntity document = requireDocument(documentId);
        DocumentVersionEntity version = documentVersionMapper.selectById(versionId);
        if (version == null || !version.getDocumentId().equals(documentId)) {
            throw new BusinessException("DOCUMENT_VERSION_NOT_FOUND", "文档版本不存在", HttpStatus.NOT_FOUND);
        }
        return submitParseTask(document, version, retryCount);
    }

    private DocumentParseTaskEntity submitParseTask(DocumentEntity document, DocumentVersionEntity version, int retryCount) {
        DocumentParseTaskEntity existingTask = documentParseTaskMapper.selectOne(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getDocumentId, document.getId())
                .eq(DocumentParseTaskEntity::getDocumentVersionId, version.getId())
                .in(DocumentParseTaskEntity::getTaskStatus, "WAITING", "RUNNING")
                .last("LIMIT 1"));
        if (existingTask != null) {
            return existingTask;
        }

        DocumentParseTaskEntity task = new DocumentParseTaskEntity();
        task.setKbId(document.getKbId());
        task.setDocumentId(document.getId());
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

    @Transactional
    public void completeInternalTask(Long taskId, InternalTaskCompleteRequest request) {
        DocumentParseTaskEntity task = documentParseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("TASK_NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND);
        }
        if (!"SUCCESS".equals(request.getTaskStatus()) && !"FAILED".equals(request.getTaskStatus())) {
            throw new BusinessException("TASK_STATUS_INVALID", "内部回调状态仅允许 SUCCESS 或 FAILED", HttpStatus.BAD_REQUEST);
        }
        if (!"SUCCESS".equals(request.getParseStatus()) && !"FAILED".equals(request.getParseStatus())) {
            throw new BusinessException("PARSE_STATUS_INVALID", "解析状态仅允许 SUCCESS 或 FAILED", HttpStatus.BAD_REQUEST);
        }

        DocumentEntity document = requireDocument(task.getDocumentId());
        DocumentVersionEntity version = documentVersionMapper.selectById(task.getDocumentVersionId());
        if (version == null) {
            throw new BusinessException("DOCUMENT_VERSION_NOT_FOUND", "文档版本不存在", HttpStatus.NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        task.setTaskStatus(request.getTaskStatus());
        task.setFinishedAt(now);
        task.setErrorMessage(request.getErrorMessage());
        documentParseTaskMapper.updateById(task);

        document.setParseStatus(request.getParseStatus());
        documentMapper.updateById(document);
        version.setParseStatus(request.getParseStatus());
        version.setParseFinishedAt(now);
        documentVersionMapper.updateById(version);
    }

    private DocumentResponse toResponse(DocumentEntity document) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(document.getKbId());
        return new DocumentResponse(
                document.getId(),
                document.getKbId(),
                knowledgeBase.getKbName(),
                document.getDocName(),
                document.getDocType(),
                document.getStorageBucket(),
                document.getStorageObjectKey(),
                document.getCurrentVersion(),
                document.getParseStatus(),
                document.getEnabled(),
                document.getFileSize(),
                document.getContentHash(),
                document.getCreatedAt(),
                document.getUpdatedAt()
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

    private DocumentVersionResponse toVersionResponse(DocumentVersionEntity version, Long documentId) {
        DocumentEntity document = requireDocument(documentId);
        return new DocumentVersionResponse(
                version.getId(),
                version.getVersionNo(),
                version.getStorageBucket(),
                version.getStorageObjectKey(),
                version.getContentHash(),
                version.getParseStatus(),
                version.getVersionNo().equals(document.getCurrentVersion()),
                version.getParseStartedAt(),
                version.getParseFinishedAt(),
                version.getCreatedAt()
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
