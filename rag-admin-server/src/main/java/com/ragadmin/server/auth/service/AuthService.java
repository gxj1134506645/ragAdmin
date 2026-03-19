package com.ragadmin.server.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.auth.config.AuthProperties;
import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginRequest;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.dto.RefreshTokenResponse;
import com.ragadmin.server.auth.entity.SysUserEntity;
import com.ragadmin.server.auth.mapper.AuthUserStructMapper;
import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.mapper.SysUserMapper;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    public static final String REQUEST_ATTRIBUTE = "AUTHENTICATED_USER";
    public static final String ADMIN_LOGIN_TYPE = "admin";
    public static final String APP_LOGIN_TYPE = "app";
    private static final List<String> ADMIN_PORTAL_ROLE_CODES = List.of("ADMIN", "KB_ADMIN", "AUDITOR");
    private static final List<String> APP_PORTAL_ROLE_CODES = List.of("APP_USER", "ADMIN", "KB_ADMIN", "AUDITOR");

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SaTokenLoginService saTokenLoginService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private AuthUserStructMapper authUserStructMapper;

    @Autowired
    private AdminPermissionService adminPermissionService;

    public LoginResponse loginForAdminPortal(LoginRequest request) {
        return loginWithRoleBoundary(
                request,
                ADMIN_PORTAL_ROLE_CODES,
                "当前账号未开通后台管理权限",
                ADMIN_LOGIN_TYPE
        );
    }

    public LoginResponse loginForAppPortal(LoginRequest request) {
        return loginWithRoleBoundary(
                request,
                APP_PORTAL_ROLE_CODES,
                "当前账号未开通问答前台权限",
                APP_LOGIN_TYPE
        );
    }

    public void assertAnyRole(Long userId, List<String> allowedRoleCodes, String message) {
        List<String> userRoleCodes = loadRoleCodes(userId);
        if (!hasAnyRole(userRoleCodes, allowedRoleCodes)) {
            throw forbidden(message);
        }
    }

    private LoginResponse loginWithRoleBoundary(
            LoginRequest request,
            List<String> allowedRoleCodes,
            String forbiddenMessage,
            String loginType
    ) {
        SysUserEntity user = findByLoginId(request.getLoginId());
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !"ENABLED".equals(user.getStatus())) {
            throw unauthorized("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw unauthorized("用户名或密码错误");
        }
        List<String> roleCodes = loadRoleCodes(user.getId());
        if (!hasAnyRole(roleCodes, allowedRoleCodes)) {
            throw forbidden(forbiddenMessage);
        }

        String accessToken = saTokenLoginService.login(user.getId(), loginType);
        String refreshToken = UUID.randomUUID().toString();
        persistRefreshSession(user.getId(), loginType, accessToken, refreshToken);

        return new LoginResponse()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setExpiresIn(authProperties.getAccessTokenTtlSeconds())
                .setRefreshExpiresIn(authProperties.getRefreshTokenTtlSeconds())
                .setUser(buildCurrentUser(user, roleCodes, loginType));
    }

    public RefreshTokenResponse refreshForAdminPortal(String refreshToken) {
        return refresh(refreshToken, ADMIN_LOGIN_TYPE);
    }

    public RefreshTokenResponse refreshForAppPortal(String refreshToken) {
        return refresh(refreshToken, APP_LOGIN_TYPE);
    }

    private RefreshTokenResponse refresh(String refreshToken, String expectedLoginType) {
        String loginType = stringRedisTemplate.opsForValue().get(buildRefreshLoginTypeKey(refreshToken));
        String userIdValue = stringRedisTemplate.opsForValue().get(buildRefreshUserIdKey(refreshToken));
        String oldAccessToken = stringRedisTemplate.opsForValue().get(buildRefreshAccessTokenKey(refreshToken));
        if (!StringUtils.hasText(loginType) || !StringUtils.hasText(userIdValue) || !StringUtils.hasText(oldAccessToken)) {
            throw unauthorized("Refresh Token 无效或已失效");
        }
        if (!expectedLoginType.equals(loginType)) {
            throw unauthorized("Refresh Token 与当前登录入口不匹配");
        }
        Long userId = Long.valueOf(userIdValue);
        SysUserEntity user = loadEnabledUser(userId);
        saTokenLoginService.logoutByTokenValue(oldAccessToken, loginType);
        String newAccessToken = saTokenLoginService.login(user.getId(), loginType);
        String newRefreshToken = UUID.randomUUID().toString();
        deleteRefreshSession(refreshToken, oldAccessToken);
        persistRefreshSession(user.getId(), loginType, newAccessToken, newRefreshToken);
        return new RefreshTokenResponse()
                .setAccessToken(newAccessToken)
                .setRefreshToken(newRefreshToken)
                .setExpiresIn(authProperties.getAccessTokenTtlSeconds())
                .setRefreshExpiresIn(authProperties.getRefreshTokenTtlSeconds());
    }

    public void logout(AuthenticatedUser authenticatedUser) {
        saTokenLoginService.logoutByTokenValue(authenticatedUser.getTokenValue(), authenticatedUser.getLoginType());
        deleteRefreshSessionByAccessToken(authenticatedUser.getTokenValue());
    }

    public CurrentUserResponse getCurrentUserForAdminPortal(Long userId) {
        return buildCurrentUser(loadEnabledUser(userId), ADMIN_LOGIN_TYPE);
    }

    public CurrentUserResponse getCurrentUserForAppPortal(Long userId) {
        return buildCurrentUser(loadEnabledUser(userId), APP_LOGIN_TYPE);
    }

    public AuthenticatedUser authenticateAccessToken(String accessToken, String loginType) {
        Long userId = saTokenLoginService.getLoginId(accessToken, loginType);
        if (userId == null) {
            throw unauthorized("Token 无效或已过期");
        }
        SysUserEntity user = loadEnabledUser(userId);
        return new AuthenticatedUser()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setSessionId(accessToken)
                .setLoginType(loginType)
                .setTokenValue(accessToken);
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

    private CurrentUserResponse buildCurrentUser(SysUserEntity user, String loginType) {
        return buildCurrentUser(user, loadRoleCodes(user.getId()), loginType);
    }

    private CurrentUserResponse buildCurrentUser(SysUserEntity user, List<String> roleCodes, String loginType) {
        List<String> permissions = ADMIN_LOGIN_TYPE.equals(loginType)
                ? adminPermissionService.listPermissionsByRoleCodes(roleCodes)
                : Collections.emptyList();
        return authUserStructMapper.toCurrentUserResponse(user, roleCodes, permissions);
    }

    private List<String> loadRoleCodes(Long userId) {
        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(userId);
        return roleCodes == null ? Collections.emptyList() : roleCodes;
    }

    private boolean hasAnyRole(List<String> userRoleCodes, List<String> allowedRoleCodes) {
        return userRoleCodes.stream().anyMatch(allowedRoleCodes::contains);
    }

    /**
     * 刷新 Token 仍然保留独立串值，目的是兼容当前前端存量协议，而不是继续保留旧 JWT 链路。
     */
    private void persistRefreshSession(Long userId, String loginType, String accessToken, String refreshToken) {
        Duration ttl = Duration.ofSeconds(authProperties.getRefreshTokenTtlSeconds());
        stringRedisTemplate.opsForValue().set(buildRefreshUserIdKey(refreshToken), String.valueOf(userId), ttl);
        stringRedisTemplate.opsForValue().set(buildRefreshLoginTypeKey(refreshToken), loginType, ttl);
        stringRedisTemplate.opsForValue().set(buildRefreshAccessTokenKey(refreshToken), accessToken, ttl);
        stringRedisTemplate.opsForValue().set(buildAccessRefreshTokenKey(accessToken), refreshToken, ttl);
    }

    private void deleteRefreshSessionByAccessToken(String accessToken) {
        String refreshToken = stringRedisTemplate.opsForValue().get(buildAccessRefreshTokenKey(accessToken));
        if (StringUtils.hasText(refreshToken)) {
            deleteRefreshSession(refreshToken, accessToken);
        } else {
            stringRedisTemplate.delete(buildAccessRefreshTokenKey(accessToken));
        }
    }

    private void deleteRefreshSession(String refreshToken, String accessToken) {
        stringRedisTemplate.delete(List.of(
                buildRefreshUserIdKey(refreshToken),
                buildRefreshLoginTypeKey(refreshToken),
                buildRefreshAccessTokenKey(refreshToken),
                buildAccessRefreshTokenKey(accessToken)
        ));
    }

    private String buildRefreshUserIdKey(String refreshToken) {
        return "rag:auth:refresh:user:" + refreshToken;
    }

    private String buildRefreshLoginTypeKey(String refreshToken) {
        return "rag:auth:refresh:type:" + refreshToken;
    }

    private String buildRefreshAccessTokenKey(String refreshToken) {
        return "rag:auth:refresh:access:" + refreshToken;
    }

    private String buildAccessRefreshTokenKey(String accessToken) {
        return "rag:auth:access:refresh:" + accessToken;
    }

    private BusinessException unauthorized(String message) {
        return new BusinessException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
