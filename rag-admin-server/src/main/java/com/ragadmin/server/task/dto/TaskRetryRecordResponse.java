package com.ragadmin.server.task.dto;

import java.time.LocalDateTime;

public record TaskRetryRecordResponse(
        Integer retryNo,
        String retryReason,
        String retryResult,
        LocalDateTime createdAt
) {
}
