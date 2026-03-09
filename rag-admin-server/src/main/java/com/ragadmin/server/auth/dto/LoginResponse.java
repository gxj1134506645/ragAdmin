package com.ragadmin.server.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        CurrentUserResponse user
) {
}
