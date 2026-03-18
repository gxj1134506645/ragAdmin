package com.ragadmin.server.audit.config;

import com.ragadmin.server.audit.entity.AuditLogEntity;
import com.ragadmin.server.audit.service.AuditLogService;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final Pattern FIRST_NUMBER = Pattern.compile("/(\\d+)(?:/|$)");

    private final AuditLogService auditLogService;

    public AuditLogInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return;
        }

        AuditLogEntity entity = new AuditLogEntity();
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(AuthService.REQUEST_ATTRIBUTE);
        if (user != null) {
            entity.setOperatorUserId(user.getUserId());
            entity.setOperatorUsername(user.getUsername());
        }
        entity.setActionType(request.getMethod());
        entity.setBizType(resolveBizType(request.getRequestURI()));
        entity.setBizId(resolveBizId(request.getRequestURI()));
        entity.setRequestMethod(request.getMethod());
        entity.setRequestPath(request.getRequestURI());
        entity.setRequestIp(request.getRemoteAddr());
        entity.setRequestPayload(extractPayload(request));
        entity.setResponseCode(String.valueOf(response.getStatus()));
        entity.setSuccess(ex == null && response.getStatus() < 400);
        auditLogService.save(entity);
    }

    // 对请求体做简单脱敏，避免密码和 token 原文进入审计表。
    private String extractPayload(HttpServletRequest request) {
        String payload = null;
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] body = wrapper.getContentAsByteArray();
            if (body.length > 0) {
                payload = new String(body, StandardCharsets.UTF_8);
            }
        }
        if ((payload == null || payload.isBlank()) && request.getQueryString() != null) {
            payload = request.getQueryString();
        }
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String sanitized = payload
                .replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"")
                .replaceAll("(?i)\"accessToken\"\\s*:\\s*\"[^\"]*\"", "\"accessToken\":\"***\"")
                .replaceAll("(?i)\"refreshToken\"\\s*:\\s*\"[^\"]*\"", "\"refreshToken\":\"***\"");
        return sanitized.length() > 2000 ? sanitized.substring(0, 2000) : sanitized;
    }

    private String resolveBizType(String path) {
        if (path.contains("/auth")) {
            return "AUTH";
        }
        if (path.contains("/chat/messages/") && path.contains("/feedback")) {
            return "CHAT_FEEDBACK";
        }
        if (path.contains("/knowledge-bases")) {
            return "KNOWLEDGE_BASE";
        }
        if (path.contains("/documents")) {
            return "DOCUMENT";
        }
        if (path.contains("/tasks")) {
            return "TASK";
        }
        if (path.contains("/chat")) {
            return "CHAT";
        }
        if (path.contains("/models") || path.contains("/model-providers")) {
            return "MODEL";
        }
        if (path.contains("/audit-logs")) {
            return "AUDIT";
        }
        return "SYSTEM";
    }

    private Long resolveBizId(String path) {
        Matcher matcher = FIRST_NUMBER.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }
}
