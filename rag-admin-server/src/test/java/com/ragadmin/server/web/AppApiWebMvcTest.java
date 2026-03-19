package com.ragadmin.server.web;

import com.ragadmin.server.app.controller.AppAuthController;
import com.ragadmin.server.app.controller.AppKnowledgeBaseController;
import com.ragadmin.server.app.controller.AppModelController;
import com.ragadmin.server.app.service.AppPortalService;
import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.dto.RefreshTokenResponse;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthInterceptor;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.common.exception.GlobalExceptionHandler;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.knowledge.dto.KnowledgeBaseResponse;
import com.ragadmin.server.model.dto.ModelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppApiWebMvcTest {

    @Mock
    private AppPortalService appPortalService;

    @Mock
    private AuthService authService;

    private MockMvc publicMockMvc;
    private MockMvc protectedMockMvc;

    @BeforeEach
    void setUp() {
        AppAuthController appAuthController = new AppAuthController();
        ReflectionTestUtils.setField(appAuthController, "appPortalService", appPortalService);

        AppKnowledgeBaseController appKnowledgeBaseController = new AppKnowledgeBaseController();
        ReflectionTestUtils.setField(appKnowledgeBaseController, "appPortalService", appPortalService);

        AppModelController appModelController = new AppModelController();
        ReflectionTestUtils.setField(appModelController, "appPortalService", appPortalService);

        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        AuthInterceptor authInterceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(authInterceptor, "authService", authService);

        publicMockMvc = MockMvcBuilders.standaloneSetup(appAuthController)
                .setControllerAdvice(exceptionHandler)
                .build();

        protectedMockMvc = MockMvcBuilders.standaloneSetup(
                        appAuthController,
                        appKnowledgeBaseController,
                        appModelController
                )
                .addInterceptors(authInterceptor)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void shouldAllowAppLoginWithoutBearerToken() throws Exception {
        CurrentUserResponse currentUser = new CurrentUserResponse()
                .setId(2L)
                .setUsername("app-user")
                .setDisplayName("问答前台用户")
                .setRoles(List.of("APP_USER"));

        LoginResponse response = new LoginResponse()
                .setAccessToken("app-access-token")
                .setRefreshToken("app-refresh-token")
                .setExpiresIn(7200)
                .setRefreshExpiresIn(604800)
                .setUser(currentUser);

        when(appPortalService.login(any())).thenReturn(response);

        publicMockMvc.perform(post("/api/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "app-user",
                                  "password": "App@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").value("app-access-token"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("APP_USER"));

        verify(authService, never()).authenticateAccessToken(any());
    }

    @Test
    void shouldAllowAppRefreshWithoutBearerToken() throws Exception {
        RefreshTokenResponse response = new RefreshTokenResponse()
                .setAccessToken("new-app-access-token")
                .setRefreshToken("new-app-refresh-token")
                .setExpiresIn(7200)
                .setRefreshExpiresIn(604800);

        when(appPortalService.refresh("old-app-refresh-token")).thenReturn(response);

        publicMockMvc.perform(post("/api/app/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "old-app-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").value("new-app-access-token"));
    }

    @Test
    void shouldRejectProtectedAppApiWithoutBearerToken() throws Exception {
        protectedMockMvc.perform(get("/api/app/knowledge-bases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("缺少有效的 Bearer Token"));
    }

    @Test
    void shouldReturnCurrentAppUserWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(appPortalService.getCurrentUser(2L)).thenReturn(new CurrentUserResponse()
                .setId(2L)
                .setUsername("app-user")
                .setDisplayName("问答前台用户")
                .setRoles(List.of("APP_USER")));

        protectedMockMvc.perform(get("/api/app/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value("app-user"));
    }

    @Test
    void shouldReturnVisibleKnowledgeBasesWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(appPortalService.listVisibleKnowledgeBases(eq("制度"), eq(1L), eq(20L))).thenReturn(new PageResponse<>(
                List.of(new KnowledgeBaseResponse(
                        11L,
                        "policy-kb",
                        "制度知识库",
                        "制度与流程",
                        2L,
                        "bge-m3",
                        1L,
                        "qwen-max",
                        5,
                        false,
                        "ENABLED"
                )),
                1,
                20,
                1
        ));

        protectedMockMvc.perform(get("/api/app/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .param("keyword", "制度"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.list[0].kbCode").value("policy-kb"));
    }

    @Test
    void shouldReturnAvailableChatModelsWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(appPortalService.listAvailableChatModels(eq("BAILIAN"), eq(1L), eq(20L))).thenReturn(new PageResponse<>(
                List.of(new ModelResponse(
                        1L,
                        1L,
                        "BAILIAN",
                        "阿里百炼",
                        "qwen-max",
                        "通义千问 Max",
                        List.of("TEXT_GENERATION"),
                        "CHAT",
                        8000,
                        new BigDecimal("0.7"),
                        "ENABLED"
                )),
                1,
                20,
                1
        ));

        protectedMockMvc.perform(get("/api/app/models")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .param("providerCode", "BAILIAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.list[0].modelCode").value("qwen-max"))
                .andExpect(jsonPath("$.data.list[0].modelType").value("CHAT"));
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser()
                .setUserId(2L)
                .setUsername("app-user")
                .setSessionId("session-app");
    }
}
