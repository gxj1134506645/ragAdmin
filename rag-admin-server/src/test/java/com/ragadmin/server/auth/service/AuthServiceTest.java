package com.ragadmin.server.auth.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SaTokenLoginService saTokenLoginService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthUserStructMapper authUserStructMapper;

    @Mock
    private AdminPermissionService adminPermissionService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(authService, "sysRoleMapper", sysRoleMapper);
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(authService, "saTokenLoginService", saTokenLoginService);
        ReflectionTestUtils.setField(authService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(authService, "authUserStructMapper", authUserStructMapper);
        ReflectionTestUtils.setField(authService, "adminPermissionService", adminPermissionService);
        ReflectionTestUtils.setField(authService, "authProperties", new AuthProperties()
                .setAccessTokenTtlSeconds(7200)
                .setRefreshTokenTtlSeconds(604800));
    }

    @Test
    void shouldLoginAndPersistSession() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("admin");
        request.setPassword("Admin@123456");

        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("hashed-password");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        CurrentUserResponse currentUser = new CurrentUserResponse()
                .setId(1L)
                .setUsername("admin")
                .setDisplayName("系统管理员")
                .setRoles(List.of("ADMIN"))
                .setPermissions(List.of("DASHBOARD_VIEW", "USER_MANAGE"));

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Admin@123456", "hashed-password")).thenReturn(true);
        when(saTokenLoginService.login(1L, AuthService.ADMIN_LOGIN_TYPE)).thenReturn("access-token");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(adminPermissionService.listPermissionsByRoleCodes(List.of("ADMIN")))
                .thenReturn(List.of("DASHBOARD_VIEW", "USER_MANAGE"));
        when(authUserStructMapper.toCurrentUserResponse(
                user,
                List.of("ADMIN"),
                List.of("DASHBOARD_VIEW", "USER_MANAGE")
        )).thenReturn(currentUser);

        LoginResponse response = authService.loginForAdminPortal(request);

        assertEquals("access-token", response.getAccessToken());
        assertTrue(response.getRefreshToken() != null && !response.getRefreshToken().isBlank());
        assertEquals(7200, response.getExpiresIn());
        assertEquals(604800, response.getRefreshExpiresIn());
        assertEquals("admin", response.getUser().getUsername());
        assertTrue(response.getUser().getPermissions().contains("USER_MANAGE"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations, times(6)).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertTrue(keyCaptor.getAllValues().stream().anyMatch(key -> key.startsWith("rag:auth:refresh:user:")));
        assertTrue(keyCaptor.getAllValues().stream().anyMatch(key -> key.startsWith("rag:auth:refresh:type:")));
        assertTrue(keyCaptor.getAllValues().stream().anyMatch(key -> key.startsWith("rag:auth:refresh:access:")));
        assertTrue(keyCaptor.getAllValues().stream().anyMatch(key -> key.startsWith("rag:auth:access:refresh:access-token")));
        assertTrue(keyCaptor.getAllValues().stream().anyMatch(key -> key.startsWith("rag:auth:meta:last-login:admin:1")));
        assertTrue(keyCaptor.getAllValues().stream().anyMatch(key -> key.startsWith("rag:auth:meta:last-active:admin:1")));
        assertTrue(valueCaptor.getAllValues().contains("1"));
        assertTrue(valueCaptor.getAllValues().contains(AuthService.ADMIN_LOGIN_TYPE));
        assertTrue(valueCaptor.getAllValues().contains("access-token"));
        assertTrue(valueCaptor.getAllValues().contains(response.getRefreshToken()));
        assertEquals(Duration.ofSeconds(604800), ttlCaptor.getAllValues().get(0));
    }

    @Test
    void shouldRejectRefreshWhenStoredTokenMismatch() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rag:auth:refresh:type:bad-refresh-token")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.refreshForAdminPortal("bad-refresh-token")
        );

        assertEquals("UNAUTHORIZED", exception.getCode());
        assertTrue(exception.getMessage().contains("Refresh Token 无效或已失效"));
        verify(sysUserMapper, never()).selectById(any());
    }

    @Test
    void shouldRefreshTokenWhenSessionAlive() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rag:auth:refresh:type:refresh-token-old")).thenReturn(AuthService.ADMIN_LOGIN_TYPE);
        when(valueOperations.get("rag:auth:refresh:user:refresh-token-old")).thenReturn("1");
        when(valueOperations.get("rag:auth:refresh:access:refresh-token-old")).thenReturn("access-token-old");
        when(sysUserMapper.selectById(1L)).thenReturn(user);
        when(saTokenLoginService.login(1L, AuthService.ADMIN_LOGIN_TYPE)).thenReturn("access-token-new");

        RefreshTokenResponse response = authService.refreshForAdminPortal("refresh-token-old");

        assertEquals("access-token-new", response.getAccessToken());
        assertTrue(response.getRefreshToken() != null && !response.getRefreshToken().isBlank());
        assertEquals(7200, response.getExpiresIn());
        assertEquals(604800, response.getRefreshExpiresIn());
        verify(saTokenLoginService).logoutByTokenValue("access-token-old", AuthService.ADMIN_LOGIN_TYPE);
        verify(stringRedisTemplate).delete(List.of(
                "rag:auth:refresh:user:refresh-token-old",
                "rag:auth:refresh:type:refresh-token-old",
                "rag:auth:refresh:access:refresh-token-old",
                "rag:auth:access:refresh:access-token-old"
        ));
    }

    @Test
    void shouldDeleteSessionKeysOnLogout() {
        AuthenticatedUser user = new AuthenticatedUser()
                .setUserId(1L)
                .setUsername("admin")
                .setLoginType(AuthService.ADMIN_LOGIN_TYPE)
                .setTokenValue("access-token");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rag:auth:access:refresh:access-token")).thenReturn("refresh-token");

        authService.logout(user);

        verify(saTokenLoginService).logoutByTokenValue("access-token", AuthService.ADMIN_LOGIN_TYPE);
        verify(stringRedisTemplate).delete(List.of(
                "rag:auth:refresh:user:refresh-token",
                "rag:auth:refresh:type:refresh-token",
                "rag:auth:refresh:access:refresh-token",
                "rag:auth:access:refresh:access-token"
        ));
    }

    @Test
    void shouldAllowAdminRoleToLoginAppPortal() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("admin");
        request.setPassword("Admin@123456");

        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("hashed-password");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        CurrentUserResponse currentUser = new CurrentUserResponse()
                .setId(1L)
                .setUsername("admin")
                .setDisplayName("系统管理员")
                .setRoles(List.of("ADMIN"))
                .setPermissions(List.of());

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Admin@123456", "hashed-password")).thenReturn(true);
        when(saTokenLoginService.login(1L, AuthService.APP_LOGIN_TYPE)).thenReturn("access-token");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(sysRoleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
        when(authUserStructMapper.toCurrentUserResponse(user, List.of("ADMIN"), List.of())).thenReturn(currentUser);

        LoginResponse response = authService.loginForAppPortal(request);

        assertEquals("admin", response.getUser().getUsername());
        assertTrue(response.getUser().getRoles().contains("ADMIN"));
        assertTrue(response.getUser().getPermissions().isEmpty());
    }

    @Test
    void shouldRejectAdminPortalLoginWhenUserHasOnlyAppRole() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("app-user");
        request.setPassword("App@123456");

        SysUserEntity user = new SysUserEntity();
        user.setId(2L);
        user.setUsername("app-user");
        user.setPasswordHash("hashed-password");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("App@123456", "hashed-password")).thenReturn(true);
        when(sysRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("APP_USER"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.loginForAdminPortal(request)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        assertTrue(exception.getMessage().contains("后台管理权限"));
        verify(saTokenLoginService, never()).login(any(), any());
    }

    @Test
    void shouldRejectAppPortalLoginWhenUserHasNoAllowedRole() {
        LoginRequest request = new LoginRequest();
        request.setLoginId("plain-user");
        request.setPassword("User@123456");

        SysUserEntity user = new SysUserEntity();
        user.setId(3L);
        user.setUsername("plain-user");
        user.setPasswordHash("hashed-password");
        user.setStatus("ENABLED");
        user.setDeleted(Boolean.FALSE);

        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("User@123456", "hashed-password")).thenReturn(true);
        when(sysRoleMapper.selectRoleCodesByUserId(3L)).thenReturn(List.of("USER"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.loginForAppPortal(request)
        );

        assertEquals("FORBIDDEN", exception.getCode());
        assertTrue(exception.getMessage().contains("问答前台权限"));
        verify(saTokenLoginService, never()).login(any(), any());
    }

    @Test
    void shouldRejectRoleAssertionWhenUserHasNoAllowedRole() {
        when(sysRoleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("APP_USER"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.assertAnyRole(2L, List.of("ADMIN"), "当前账号未开通用户管理权限")
        );

        assertEquals("FORBIDDEN", exception.getCode());
        assertTrue(exception.getMessage().contains("用户管理权限"));
    }
}
