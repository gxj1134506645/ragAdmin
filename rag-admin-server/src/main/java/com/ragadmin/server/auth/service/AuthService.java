package com.ragadmin.server.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.auth.config.AuthProperties;
import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginRequest;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.dto.RefreshTokenResponse;
import com.ragadmin.server.auth.entity.SysUserEntity;
import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.mapper.SysUserMapper;
import com.ragadmin.server.auth.model.AuthClaims;
import com.ragadmin.server.auth.model.AuthTokenType;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    public static final String REQUEST_ATTRIBUTE = "AUTHENTICATED_USER";

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AuthProperties authProperties;

    public LoginResponse login(LoginRequest request) {
        SysUserEntity user = findByLoginId(request.getLoginId());
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !"ENABLED".equals(user.getStatus())) {
            throw unauthorized("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw unauthorized("用户名或密码错误");
        }

        String sessionId = UUID.randomUUID().toString();
        String accessToken = tokenService.generateAccessToken(user.getId(), user.getUsername(), sessionId);
        String refreshToken = tokenService.generateRefreshToken(user.getId(), user.getUsername(), sessionId);
        persistSession(user.getId(), sessionId, refreshToken);

        return new LoginResponse(
                accessToken,
                refreshToken,
                authProperties.getAccessTokenTtlSeconds(),
                authProperties.getRefreshTokenTtlSeconds(),
                buildCurrentUser(user)
        );
    }

    public RefreshTokenResponse refresh(String refreshToken) {
        AuthClaims claims = parseToken(refreshToken, AuthTokenType.REFRESH);
        assertSessionAlive(claims.sessionId());
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(buildRefreshKey(claims.sessionId()));
        if (!StringUtils.hasText(storedRefreshToken) || !storedRefreshToken.equals(refreshToken)) {
            throw unauthorized("Refresh Token 无效或已失效");
        }

        SysUserEntity user = loadEnabledUser(claims.userId());
        String newAccessToken = tokenService.generateAccessToken(user.getId(), user.getUsername(), claims.sessionId());
        String newRefreshToken = tokenService.generateRefreshToken(user.getId(), user.getUsername(), claims.sessionId());
        persistSession(user.getId(), claims.sessionId(), newRefreshToken);
        return new RefreshTokenResponse(
                newAccessToken,
                newRefreshToken,
                authProperties.getAccessTokenTtlSeconds(),
                authProperties.getRefreshTokenTtlSeconds()
        );
    }

    public void logout(AuthenticatedUser authenticatedUser) {
        stringRedisTemplate.delete(List.of(
                buildSessionKey(authenticatedUser.sessionId()),
                buildRefreshKey(authenticatedUser.sessionId())
        ));
    }

    public CurrentUserResponse getCurrentUser(Long userId) {
        return buildCurrentUser(loadEnabledUser(userId));
    }

    public AuthenticatedUser authenticateAccessToken(String accessToken) {
        AuthClaims claims = parseToken(accessToken, AuthTokenType.ACCESS);
        assertSessionAlive(claims.sessionId());
        return new AuthenticatedUser(claims.userId(), claims.username(), claims.sessionId());
    }

    private SysUserEntity findByLoginId(String loginId) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .and(wrapper -> wrapper.eq(SysUserEntity::getUsername, loginId)
                        .or()
                        .eq(SysUserEntity::getMobile, loginId))
                .last("LIMIT 1"));
    }

    private SysUserEntity loadEnabledUser(Long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !"ENABLED".equals(user.getStatus())) {
            throw unauthorized("用户不存在或已禁用");
        }
        return user;
    }

    private CurrentUserResponse buildCurrentUser(SysUserEntity user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getMobile(),
                sysRoleMapper.selectRoleCodesByUserId(user.getId())
        );
    }

    private AuthClaims parseToken(String token, AuthTokenType expectedType) {
        try {
            AuthClaims claims = tokenService.parse(token);
            if (claims.tokenType() != expectedType) {
                throw unauthorized("Token 类型非法");
            }
            return claims;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unauthorized("Token 无效或已过期");
        }
    }

    private void assertSessionAlive(String sessionId) {
        Boolean exists = stringRedisTemplate.hasKey(buildSessionKey(sessionId));
        if (!Boolean.TRUE.equals(exists)) {
            throw unauthorized("登录态已失效，请重新登录");
        }
    }

    private void persistSession(Long userId, String sessionId, String refreshToken) {
        Duration ttl = Duration.ofSeconds(authProperties.getRefreshTokenTtlSeconds());
        stringRedisTemplate.opsForValue().set(buildSessionKey(sessionId), String.valueOf(userId), ttl);
        stringRedisTemplate.opsForValue().set(buildRefreshKey(sessionId), refreshToken, ttl);
    }

    private String buildSessionKey(String sessionId) {
        return "rag:auth:session:" + sessionId;
    }

    private String buildRefreshKey(String sessionId) {
        return "rag:auth:refresh:" + sessionId;
    }

    private BusinessException unauthorized(String message) {
        return new BusinessException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }
}
