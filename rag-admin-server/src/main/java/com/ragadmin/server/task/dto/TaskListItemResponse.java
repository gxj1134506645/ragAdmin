package com.ragadmin.server.task.dto;

import java.time.LocalDateTime;

public record TaskListItemResponse(
        Long taskId,
        String taskType,
        String taskStatus,
        String bizType,
        Long bizId,
        Long kbId,
        Long documentId,
        String documentName,
        Integer retryCount,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
