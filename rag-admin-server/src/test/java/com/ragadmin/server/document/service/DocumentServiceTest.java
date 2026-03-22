package com.ragadmin.server.document.service;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.dto.ActivateDocumentVersionResponse;
import com.ragadmin.server.document.dto.CreateDocumentRequest;
import com.ragadmin.server.document.dto.CreateDocumentVersionRequest;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.mapper.DocumentVersionMapper;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.task.service.TaskRealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentVersionMapper documentVersionMapper;

    @Mock
    private DocumentParseTaskMapper documentParseTaskMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private TaskRealtimeEventService taskRealtimeEventService;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void shouldUpdateMainDocumentWhenCreatingNewVersion() {
        DocumentEntity document = new DocumentEntity();
        document.setId(101L);
        document.setKbId(1001L);
        document.setCurrentVersion(1);
        document.setParseStatus("SUCCESS");

        CreateDocumentVersionRequest request = new CreateDocumentVersionRequest();
        request.setStorageBucket("bucket-v2");
        request.setStorageObjectKey("docs/manual-v2.md");
        request.setContentHash("hash-v2");
        request.setFileSize(2048L);

        when(documentMapper.selectById(101L)).thenReturn(document);
        when(knowledgeBaseService.requireById(1001L)).thenReturn(mockKnowledgeBase(1001L, "测试知识库"));

        documentService.createDocumentVersion(101L, request, 1L);

        ArgumentCaptor<DocumentVersionEntity> versionCaptor = ArgumentCaptor.forClass(DocumentVersionEntity.class);
        verify(documentVersionMapper).insert(versionCaptor.capture());
        DocumentVersionEntity insertedVersion = versionCaptor.getValue();
        assertEquals(101L, insertedVersion.getDocumentId());
        assertEquals(2, insertedVersion.getVersionNo());
        assertEquals("PENDING", insertedVersion.getParseStatus());

        ArgumentCaptor<DocumentEntity> documentCaptor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentMapper).updateById(documentCaptor.capture());
        DocumentEntity updatedDocument = documentCaptor.getValue();
        assertEquals(2, updatedDocument.getCurrentVersion());
        assertEquals("PENDING", updatedDocument.getParseStatus());
        assertEquals("bucket-v2", updatedDocument.getStorageBucket());
        assertEquals("docs/manual-v2.md", updatedDocument.getStorageObjectKey());
        assertEquals("hash-v2", updatedDocument.getContentHash());
        assertEquals(2048L, updatedDocument.getFileSize());
    }

    @Test
    void shouldSyncDocumentFieldsWhenActivatingHistoricalVersion() {
        DocumentEntity document = new DocumentEntity();
        document.setId(201L);
        document.setKbId(2001L);
        document.setCurrentVersion(3);
        document.setParseStatus("FAILED");

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setId(301L);
        version.setDocumentId(201L);
        version.setVersionNo(2);
        version.setStorageBucket("archive-bucket");
        version.setStorageObjectKey("archive/manual-v2.md");
        version.setContentHash("archive-hash");
        version.setParseStatus("SUCCESS");

        when(documentMapper.selectById(201L)).thenReturn(document);
        when(documentVersionMapper.selectById(301L)).thenReturn(version);

        ActivateDocumentVersionResponse response = documentService.activateVersion(201L, 301L);

        verify(documentMapper).updateById(document);
        assertEquals(2, document.getCurrentVersion());
        assertEquals("SUCCESS", document.getParseStatus());
        assertEquals("archive-bucket", document.getStorageBucket());
        assertEquals("archive/manual-v2.md", document.getStorageObjectKey());
        assertEquals("archive-hash", document.getContentHash());
        assertEquals(201L, response.documentId());
        assertEquals(301L, response.versionId());
        assertEquals(2, response.currentVersion());
    }

    @Test
    void shouldReturnExistingWaitingTaskForCurrentVersion() {
        DocumentEntity document = new DocumentEntity();
        document.setId(401L);
        document.setKbId(11L);
        document.setCurrentVersion(3);

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setId(501L);
        version.setDocumentId(401L);
        version.setVersionNo(3);

        DocumentParseTaskEntity existingTask = new DocumentParseTaskEntity();
        existingTask.setId(601L);
        existingTask.setDocumentId(401L);
        existingTask.setDocumentVersionId(501L);
        existingTask.setTaskStatus("WAITING");

        when(documentMapper.selectById(401L)).thenReturn(document);
        when(documentVersionMapper.selectOne(any())).thenReturn(version);
        when(documentParseTaskMapper.selectOne(any())).thenReturn(existingTask);

        DocumentParseTaskEntity result = documentService.submitParseTask(401L, 0);

        assertSame(existingTask, result);
        verify(documentParseTaskMapper, never()).insert(any(DocumentParseTaskEntity.class));
        verify(documentMapper, never()).updateById(any(DocumentEntity.class));
        verify(documentVersionMapper, never()).updateById(any(DocumentVersionEntity.class));
    }

    @Test
    void shouldCreateRetryTaskForSpecifiedVersion() {
        DocumentEntity document = new DocumentEntity();
        document.setId(701L);
        document.setKbId(21L);
        document.setCurrentVersion(5);
        document.setParseStatus("FAILED");

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setId(801L);
        version.setDocumentId(701L);
        version.setVersionNo(2);
        version.setParseStatus("FAILED");

        when(documentMapper.selectById(701L)).thenReturn(document);
        when(documentVersionMapper.selectById(801L)).thenReturn(version);
        when(documentParseTaskMapper.selectOne(any())).thenReturn(null);

        documentService.submitParseTask(701L, 801L, 2);

        ArgumentCaptor<DocumentParseTaskEntity> taskCaptor = ArgumentCaptor.forClass(DocumentParseTaskEntity.class);
        verify(documentParseTaskMapper).insert(taskCaptor.capture());
        DocumentParseTaskEntity insertedTask = taskCaptor.getValue();
        assertEquals(21L, insertedTask.getKbId());
        assertEquals(701L, insertedTask.getDocumentId());
        assertEquals(801L, insertedTask.getDocumentVersionId());
        assertEquals("WAITING", insertedTask.getTaskStatus());
        assertEquals(2, insertedTask.getRetryCount());

        verify(documentMapper).updateById(document);
        verify(documentVersionMapper).updateById(version);
        verify(taskRealtimeEventService).publishTaskQueued(insertedTask, document);
        assertEquals("PENDING", document.getParseStatus());
        assertEquals("PENDING", version.getParseStatus());
    }

    @Test
    void shouldRejectRetryWhenVersionDoesNotBelongToDocument() {
        DocumentEntity document = new DocumentEntity();
        document.setId(901L);

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setId(902L);
        version.setDocumentId(999L);

        when(documentMapper.selectById(901L)).thenReturn(document);
        when(documentVersionMapper.selectById(902L)).thenReturn(version);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentService.submitParseTask(901L, 902L, 1)
        );

        assertEquals("DOCUMENT_VERSION_NOT_FOUND", exception.getCode());
    }

    @Test
    void shouldRejectCreateDocumentWhenFileSizeIsZero() {
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setDocName("空文件.pdf");
        request.setDocType("PDF");
        request.setStorageBucket("bucket");
        request.setStorageObjectKey("docs/empty.pdf");
        request.setFileSize(0L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentService.createDocument(1001L, request, 1L)
        );

        assertEquals("DOCUMENT_FILE_EMPTY", exception.getCode());
        assertEquals("文件不能为空，请重新上传", exception.getMessage());
        verify(documentMapper, never()).insert(any(DocumentEntity.class));
        verify(documentVersionMapper, never()).insert(any(DocumentVersionEntity.class));
    }

    @Test
    void shouldRejectCreateDocumentVersionWhenFileSizeIsZero() {
        DocumentEntity document = new DocumentEntity();
        document.setId(101L);
        document.setKbId(1001L);

        CreateDocumentVersionRequest request = new CreateDocumentVersionRequest();
        request.setStorageBucket("bucket");
        request.setStorageObjectKey("docs/empty-v2.pdf");
        request.setFileSize(0L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentService.createDocumentVersion(101L, request, 1L)
        );

        assertEquals("DOCUMENT_FILE_EMPTY", exception.getCode());
        assertEquals("文件不能为空，请重新上传", exception.getMessage());
        verify(documentVersionMapper, never()).insert(any(DocumentVersionEntity.class));
        verify(documentMapper, never()).updateById(any(DocumentEntity.class));
    }

    private KnowledgeBaseEntity mockKnowledgeBase(Long kbId, String kbName) {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(kbId);
        knowledgeBase.setKbName(kbName);
        return knowledgeBase;
    }
}
