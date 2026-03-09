package com.ragadmin.server.auth.model;

public record AuthenticatedUser(Long userId, String username, String sessionId) {
}
