package com.ragadmin.server.document.dto;

public record DocumentResponse(
        Long id,
        Long kbId,
        String docName,
        String docType,
        String storageBucket,
        String storageObjectKey,
        Integer currentVersion,
        String parseStatus,
        Boolean enabled,
        Long fileSize,
        String contentHash
) {
}
