package com.ragadmin.server.document.dto;

import java.time.LocalDateTime;

public record DocumentVersionResponse(
        Long id,
        Integer versionNo,
        String storageBucket,
        String storageObjectKey,
        String contentHash,
        String parseStatus,
        Boolean active,
        LocalDateTime parseStartedAt,
        LocalDateTime parseFinishedAt,
        LocalDateTime createdAt
) {
}
