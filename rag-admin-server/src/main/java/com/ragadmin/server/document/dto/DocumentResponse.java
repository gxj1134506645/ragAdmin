package com.ragadmin.server.document.dto;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long kbId,
        String kbName,
        String docName,
        String docType,
        String storageBucket,
        String storageObjectKey,
        Integer currentVersion,
        String parseStatus,
        Boolean enabled,
        Long fileSize,
        String contentHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
