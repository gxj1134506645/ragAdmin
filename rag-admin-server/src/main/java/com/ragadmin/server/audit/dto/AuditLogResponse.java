package com.ragadmin.server.audit.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long operatorUserId,
        String operatorUsername,
        String actionType,
        String bizType,
        Long bizId,
        String requestMethod,
        String requestPath,
        String requestIp,
        String responseCode,
        Boolean success,
        LocalDateTime createdAt
) {
}
