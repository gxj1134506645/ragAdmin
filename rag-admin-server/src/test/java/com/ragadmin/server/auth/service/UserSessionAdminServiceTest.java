package com.ragadmin.server.auth.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.audit.entity.AuditLogEntity;
import com.ragadmin.server.audit.service.AuditLogService;
import com.ragadmin.server.auth.dto.KickoutUserSessionRequest;
import com.ragadmin.server.auth.dto.UserSessionListItemResponse;
import com.ragadmin.server.auth.entity.SysUserEntity;
import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.mapper.SysUserMapper;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.common.model.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionAdminServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SaTokenLoginService saTokenLoginService;

    @Mock
    private AuthService authService;

    @Mock
    private AuditLogService auditLogService;

    private UserSessionAdminService userSessionAdminService;

    @BeforeEach
    void setUp() {
        userSessionAdminService = new UserSessionAdminService();
        ReflectionTestUtils.setField(userSessionAdminService, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(userSessionAdminService, "sysRoleMapper", sysRoleMapper);
        ReflectionTestUtils.setField(userSessionAdminService, "saTokenLoginService", saTokenLoginService);
        ReflectionTestUtils.setField(userSessionAdminService, "authService", authService);
        ReflectionTestUtils.setField(userSessionAdminService, "auditLogService", auditLogService);
    }

    @Test
    void shouldListUserSessionsWithOnlineFlags() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2L);
        user.setUsername("app-user");
        user.setDisplayName("前台用户");
        user.setMobile("13900000000");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        Page<SysUserEntity> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(user));

        when(sysUserMapper.selectPage(any(), any())).thenReturn(page);
        when(sysRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("APP_USER"));
        when(saTokenLoginService.isLogin(2L, AuthService.ADMIN_LOGIN_TYPE)).thenReturn(false);
        when(saTokenLoginService.isLogin(2L, AuthService.APP_LOGIN_TYPE)).thenReturn(true);
        when(authService.loadLastLoginAt(2L, AuthService.ADMIN_LOGIN_TYPE))
                .thenReturn(LocalDateTime.of(2026, 3, 19, 8, 0));
        when(authService.loadLastActiveAt(2L, AuthService.ADMIN_LOGIN_TYPE)).thenReturn(null);
        when(authService.loadLastLoginAt(2L, AuthService.APP_LOGIN_TYPE))
                .thenReturn(LocalDateTime.of(2026, 3, 19, 9, 0));
        when(authService.loadLastActiveAt(2L, AuthService.APP_LOGIN_TYPE))
                .thenReturn(LocalDateTime.of(2026, 3, 19, 9, 30));

        PageResponse<UserSessionListItemResponse> response = userSessionAdminService.list(null, null, null, 1, 20);

        assertEquals(1, response.total());
        assertEquals(1, response.list().size());
        UserSessionListItemResponse item = response.list().get(0);
        assertEquals(2L, item.getUserId());
        assertEquals("app-user", item.getUsername());
        assertFalse(item.getAdminOnline());
        assertTrue(item.getAppOnline());
        assertEquals(LocalDateTime.of(2026, 3, 19, 9, 0), item.getLastLoginAt());
        assertEquals(LocalDateTime.of(2026, 3, 19, 9, 30), item.getLastActiveAt());
    }

    @Test
    void shouldKickoutAdminScopeAndRevokeRefreshTokens() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2L);
        user.setUsername("app-user");
        user.setDeleted(Boolean.FALSE);

        when(sysUserMapper.selectById(2L)).thenReturn(user);
        when(saTokenLoginService.getTokenValueListByLoginId(2L, AuthService.ADMIN_LOGIN_TYPE))
                .thenReturn(List.of("admin-token-1", "admin-token-2"));

        AuthenticatedUser operator = new AuthenticatedUser()
                .setUserId(1L)
                .setUsername("admin");
        KickoutUserSessionRequest request = new KickoutUserSessionRequest();
        request.setScope("admin");
        request.setReason("管理员手动下线");

        userSessionAdminService.kickout(operator, 2L, request);

        verify(authService).revokeRefreshSessionByAccessToken("admin-token-1");
        verify(authService).revokeRefreshSessionByAccessToken("admin-token-2");
        verify(saTokenLoginService).kickout(2L, AuthService.ADMIN_LOGIN_TYPE);
        verify(saTokenLoginService, never()).kickout(2L, AuthService.APP_LOGIN_TYPE);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        AuditLogEntity entity = captor.getValue();
        assertEquals(1L, entity.getOperatorUserId());
        assertEquals("admin", entity.getOperatorUsername());
        assertEquals("KICKOUT", entity.getActionType());
        assertEquals("AUTH", entity.getBizType());
        assertEquals(2L, entity.getBizId());
        assertTrue(entity.getRequestPayload().contains("\"scope\":\"admin\""));
    }
}
