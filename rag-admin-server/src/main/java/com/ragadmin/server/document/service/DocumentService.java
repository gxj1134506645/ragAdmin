package com.ragadmin.server.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.dto.CreateDocumentRequest;
import com.ragadmin.server.document.dto.DocumentResponse;
import com.ragadmin.server.document.dto.ParseDocumentResponse;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
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
    private final KnowledgeBaseService knowledgeBaseService;

    public DocumentService(
            DocumentMapper documentMapper,
            DocumentVersionMapper documentVersionMapper,
            DocumentParseTaskMapper documentParseTaskMapper,
            KnowledgeBaseService knowledgeBaseService
    ) {
        this.documentMapper = documentMapper;
        this.documentVersionMapper = documentVersionMapper;
        this.documentParseTaskMapper = documentParseTaskMapper;
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

    @Transactional
    public ParseDocumentResponse submitParseTask(Long documentId) {
        DocumentEntity document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("DOCUMENT_NOT_FOUND", "文档不存在", HttpStatus.NOT_FOUND);
        }

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
            return new ParseDocumentResponse(
                    existingTask.getId(),
                    documentId,
                    version.getId(),
                    existingTask.getTaskStatus(),
                    document.getParseStatus()
            );
        }

        DocumentParseTaskEntity task = new DocumentParseTaskEntity();
        task.setKbId(document.getKbId());
        task.setDocumentId(documentId);
        task.setDocumentVersionId(version.getId());
        task.setTaskStatus("WAITING");
        task.setRetryCount(0);
        documentParseTaskMapper.insert(task);

        document.setParseStatus("PENDING");
        documentMapper.updateById(document);
        version.setParseStatus("PENDING");
        documentVersionMapper.updateById(version);

        return new ParseDocumentResponse(task.getId(), documentId, version.getId(), task.getTaskStatus(), document.getParseStatus());
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
}
