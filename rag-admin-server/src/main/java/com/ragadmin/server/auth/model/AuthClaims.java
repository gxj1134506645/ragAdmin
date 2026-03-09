package com.ragadmin.server.auth.model;

public record AuthClaims(Long userId, String username, String sessionId, AuthTokenType tokenType) {
}
