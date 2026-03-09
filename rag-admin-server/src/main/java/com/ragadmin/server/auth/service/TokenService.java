package com.ragadmin.server.auth.service;

import com.ragadmin.server.auth.config.AuthProperties;
import com.ragadmin.server.auth.model.AuthClaims;
import com.ragadmin.server.auth.model.AuthTokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class TokenService {

    @Autowired
    private AuthProperties authProperties;

    private SecretKey secretKey;

    @Autowired
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username, String sessionId) {
        return generateToken(userId, username, sessionId, AuthTokenType.ACCESS, authProperties.getAccessTokenTtlSeconds());
    }

    public String generateRefreshToken(Long userId, String username, String sessionId) {
        return generateToken(userId, username, sessionId, AuthTokenType.REFRESH, authProperties.getRefreshTokenTtlSeconds());
    }

    public AuthClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthClaims(
                claims.get("uid", Long.class),
                claims.getSubject(),
                claims.get("sid", String.class),
                AuthTokenType.valueOf(claims.get("typ", String.class))
        );
    }

    private String generateToken(Long userId, String username, String sessionId, AuthTokenType tokenType, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("sid", sessionId)
                .claim("typ", tokenType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(secretKey)
                .compact();
    }
}
