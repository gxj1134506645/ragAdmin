package com.ragadmin.server.task.dto;

import java.time.LocalDateTime;

public record TaskStepResponse(
        String stepCode,
        String stepName,
        String stepStatus,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
