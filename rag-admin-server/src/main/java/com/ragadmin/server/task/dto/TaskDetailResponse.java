package com.ragadmin.server.task.dto;

import java.time.LocalDateTime;

public record TaskDetailResponse(
        Long taskId,
        String taskType,
        String taskStatus,
        String bizType,
        Long bizId,
        Long kbId,
        Long documentId,
        Long documentVersionId,
        String documentName,
        String documentParseStatus,
        Integer retryCount,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        java.util.List<TaskStepResponse> steps,
        java.util.List<TaskRetryRecordResponse> retryRecords
) {
}
