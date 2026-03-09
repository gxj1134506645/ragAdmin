package com.ragadmin.server.auth.dto;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn
) {
}
