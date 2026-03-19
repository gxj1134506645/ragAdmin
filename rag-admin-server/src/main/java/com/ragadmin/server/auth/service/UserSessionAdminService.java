package com.ragadmin.server.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.audit.entity.AuditLogEntity;
import com.ragadmin.server.audit.service.AuditLogService;
import com.ragadmin.server.auth.dto.KickoutUserSessionRequest;
import com.ragadmin.server.auth.dto.UserSessionDetailResponse;
import com.ragadmin.server.auth.dto.UserSessionListItemResponse;
import com.ragadmin.server.auth.entity.SysUserEntity;
import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.mapper.SysUserMapper;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserSessionAdminService {

    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z_]+$");
    private static final String ALL_SCOPE = "all";

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SaTokenLoginService saTokenLoginService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AuditLogService auditLogService;

    public PageResponse<UserSessionListItemResponse> list(
            String keyword,
            String roleCode,
            String onlineScope,
            long pageNo,
            long pageSize
    ) {
        String normalizedOnlineScope = normalizeOnlineScope(onlineScope);
        Set<Long> onlineUserIds = null;
        if (normalizedOnlineScope != null) {
            onlineUserIds = resolveScopedOnlineUserIds(normalizedOnlineScope);
            if (onlineUserIds.isEmpty()) {
                return new PageResponse<>(Collections.emptyList(), pageNo, pageSize, 0);
            }
        }

        Page<SysUserEntity> page = sysUserMapper.selectPage(
                Page.of(pageNo, pageSize),
                buildListWrapper(keyword, roleCode, onlineUserIds)
        );
        if (page.getRecords().isEmpty()) {
            return new PageResponse<>(Collections.emptyList(), pageNo, pageSize, page.getTotal());
        }

        List<UserSessionListItemResponse> list = page.getRecords().stream()
                .map(this::toListItemResponse)
                .toList();
        return new PageResponse<>(list, pageNo, pageSize, page.getTotal());
    }

    public UserSessionDetailResponse detail(Long userId) {
        SysUserEntity user = requireUser(userId);
        List<String> roleCodes = loadRoleCodes(userId);
        UserSessionSnapshot snapshot = loadSessionSnapshot(userId);
        return new UserSessionDetailResponse()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setDisplayName(user.getDisplayName())
                .setMobile(user.getMobile())
                .setStatus(user.getStatus())
                .setRoles(roleCodes)
                .setAdminOnline(snapshot.adminOnline())
                .setAppOnline(snapshot.appOnline())
                .setAdminLastLoginAt(snapshot.adminLastLoginAt())
                .setAdminLastActiveAt(snapshot.adminLastActiveAt())
                .setAppLastLoginAt(snapshot.appLastLoginAt())
                .setAppLastActiveAt(snapshot.appLastActiveAt());
    }

    public void kickout(AuthenticatedUser operator, Long targetUserId, KickoutUserSessionRequest request) {
        requireUser(targetUserId);
        String normalizedScope = normalizeKickoutScope(request.getScope());

        // 踢人下线时必须同步清理 refresh token 映射，避免旧 refresh token 换出新的 access token。
        if (AuthService.ADMIN_LOGIN_TYPE.equals(normalizedScope) || ALL_SCOPE.equals(normalizedScope)) {
            revokeRefreshSessions(targetUserId, AuthService.ADMIN_LOGIN_TYPE);
            saTokenLoginService.kickout(targetUserId, AuthService.ADMIN_LOGIN_TYPE);
        }
        if (AuthService.APP_LOGIN_TYPE.equals(normalizedScope) || ALL_SCOPE.equals(normalizedScope)) {
            revokeRefreshSessions(targetUserId, AuthService.APP_LOGIN_TYPE);
            saTokenLoginService.kickout(targetUserId, AuthService.APP_LOGIN_TYPE);
        }

        auditLogService.save(buildKickoutAuditLog(operator, targetUserId, normalizedScope, request.getReason()));
    }

    private LambdaQueryWrapper<SysUserEntity> buildListWrapper(String keyword, String roleCode, Set<Long> onlineUserIds) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeleted, Boolean.FALSE)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(SysUserEntity::getUsername, keyword)
                        .or()
                        .like(SysUserEntity::getDisplayName, keyword)
                        .or()
                        .like(SysUserEntity::getMobile, keyword))
                .in(onlineUserIds != null, SysUserEntity::getId, onlineUserIds)
                .orderByDesc(SysUserEntity::getId);
        if (StringUtils.hasText(normalizedRoleCode)) {
            wrapper.inSql(SysUserEntity::getId, buildRoleCodeSubQuery(normalizedRoleCode));
        }
        return wrapper;
    }

    private UserSessionListItemResponse toListItemResponse(SysUserEntity user) {
        List<String> roleCodes = loadRoleCodes(user.getId());
        UserSessionSnapshot snapshot = loadSessionSnapshot(user.getId());
        return new UserSessionListItemResponse()
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setDisplayName(user.getDisplayName())
                .setMobile(user.getMobile())
                .setStatus(user.getStatus())
                .setRoles(roleCodes)
                .setAdminOnline(snapshot.adminOnline())
                .setAppOnline(snapshot.appOnline())
                .setLastLoginAt(latest(snapshot.adminLastLoginAt(), snapshot.appLastLoginAt()))
                .setLastActiveAt(latest(snapshot.adminLastActiveAt(), snapshot.appLastActiveAt()));
    }

    private UserSessionSnapshot loadSessionSnapshot(Long userId) {
        return new UserSessionSnapshot(
                saTokenLoginService.isLogin(userId, AuthService.ADMIN_LOGIN_TYPE),
                saTokenLoginService.isLogin(userId, AuthService.APP_LOGIN_TYPE),
                authService.loadLastLoginAt(userId, AuthService.ADMIN_LOGIN_TYPE),
                authService.loadLastActiveAt(userId, AuthService.ADMIN_LOGIN_TYPE),
                authService.loadLastLoginAt(userId, AuthService.APP_LOGIN_TYPE),
                authService.loadLastActiveAt(userId, AuthService.APP_LOGIN_TYPE)
        );
    }

    private void revokeRefreshSessions(Long userId, String loginType) {
        saTokenLoginService.getTokenValueListByLoginId(userId, loginType)
                .forEach(authService::revokeRefreshSessionByAccessToken);
    }

    private AuditLogEntity buildKickoutAuditLog(
            AuthenticatedUser operator,
            Long targetUserId,
            String scope,
            String reason
    ) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setOperatorUserId(operator.getUserId());
        entity.setOperatorUsername(operator.getUsername());
        entity.setActionType("KICKOUT");
        entity.setBizType("AUTH");
        entity.setBizId(targetUserId);
        entity.setRequestMethod("POST");
        entity.setRequestPath("/api/admin/user-sessions/" + targetUserId + "/kickout");
        entity.setRequestPayload("{\"scope\":\"" + scope + "\",\"reason\":\"" + sanitizeReason(reason) + "\"}");
        entity.setResponseCode(String.valueOf(HttpStatus.OK.value()));
        entity.setSuccess(Boolean.TRUE);
        return entity;
    }

    private Set<Long> resolveScopedOnlineUserIds(String onlineScope) {
        if (AuthService.ADMIN_LOGIN_TYPE.equals(onlineScope)) {
            return saTokenLoginService.listOnlineUserIds(AuthService.ADMIN_LOGIN_TYPE);
        }
        if (AuthService.APP_LOGIN_TYPE.equals(onlineScope)) {
            return saTokenLoginService.listOnlineUserIds(AuthService.APP_LOGIN_TYPE);
        }
        if (ALL_SCOPE.equals(onlineScope)) {
            Set<Long> onlineUserIds = new LinkedHashSet<>(saTokenLoginService.listOnlineUserIds(AuthService.ADMIN_LOGIN_TYPE));
            onlineUserIds.addAll(saTokenLoginService.listOnlineUserIds(AuthService.APP_LOGIN_TYPE));
            return onlineUserIds;
        }
        throw badRequest("onlineScope 仅支持 admin、app、all");
    }

    private List<String> loadRoleCodes(Long userId) {
        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(userId);
        return roleCodes == null ? Collections.emptyList() : roleCodes;
    }

    private SysUserEntity requireUser(Long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        return user;
    }

    private String normalizeRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return null;
        }
        if (!ROLE_CODE_PATTERN.matcher(roleCode).matches()) {
            throw badRequest("roleCode 格式不合法");
        }
        return roleCode;
    }

    private String normalizeOnlineScope(String onlineScope) {
        if (!StringUtils.hasText(onlineScope)) {
            return null;
        }
        String normalized = onlineScope.trim().toLowerCase();
        if (AuthService.ADMIN_LOGIN_TYPE.equals(normalized)
                || AuthService.APP_LOGIN_TYPE.equals(normalized)
                || ALL_SCOPE.equals(normalized)) {
            return normalized;
        }
        throw badRequest("onlineScope 仅支持 admin、app、all");
    }

    private String normalizeKickoutScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            throw badRequest("scope 不能为空");
        }
        String normalized = scope.trim().toLowerCase();
        if (AuthService.ADMIN_LOGIN_TYPE.equals(normalized)
                || AuthService.APP_LOGIN_TYPE.equals(normalized)
                || ALL_SCOPE.equals(normalized)) {
            return normalized;
        }
        throw badRequest("scope 仅支持 admin、app、all");
    }

    private String buildRoleCodeSubQuery(String roleCode) {
        return """
                SELECT ur.user_id
                FROM sys_user_role ur
                INNER JOIN sys_role r ON ur.role_id = r.id
                WHERE r.role_code = '%s'
                """.formatted(roleCode);
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private String sanitizeReason(String reason) {
        return reason == null ? "" : reason.replace("\"", "\\\"");
    }

    private BusinessException badRequest(String message) {
        return new BusinessException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    private record UserSessionSnapshot(
            boolean adminOnline,
            boolean appOnline,
            LocalDateTime adminLastLoginAt,
            LocalDateTime adminLastActiveAt,
            LocalDateTime appLastLoginAt,
            LocalDateTime appLastActiveAt
    ) {
    }
}
