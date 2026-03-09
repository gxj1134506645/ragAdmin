package com.ragadmin.server.document.dto;

public record ParseDocumentResponse(
        Long taskId,
        Long documentId,
        Long documentVersionId,
        String taskStatus,
        String parseStatus
) {
}
