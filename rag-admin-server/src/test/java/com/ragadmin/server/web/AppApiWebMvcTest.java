package com.ragadmin.server.web;

import com.ragadmin.server.app.controller.AppChatController;
import com.ragadmin.server.app.dto.AppChatSessionResponse;
import com.ragadmin.server.app.controller.AppAuthController;
import com.ragadmin.server.app.controller.AppKnowledgeBaseController;
import com.ragadmin.server.app.controller.AppModelController;
import com.ragadmin.server.app.service.AppChatService;
import com.ragadmin.server.app.service.AppPortalService;
import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.dto.RefreshTokenResponse;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthInterceptor;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatUsageResponse;
import com.ragadmin.server.common.exception.GlobalExceptionHandler;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.knowledge.dto.KnowledgeBaseResponse;
import com.ragadmin.server.model.dto.ModelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppApiWebMvcTest {

    @Mock
    private AppPortalService appPortalService;

    @Mock
    private AppChatService appChatService;

    @Mock
    private AuthService authService;

    private MockMvc publicMockMvc;
    private MockMvc protectedMockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AppAuthController appAuthController = new AppAuthController();
        ReflectionTestUtils.setField(appAuthController, "appPortalService", appPortalService);

        AppKnowledgeBaseController appKnowledgeBaseController = new AppKnowledgeBaseController();
        ReflectionTestUtils.setField(appKnowledgeBaseController, "appPortalService", appPortalService);

        AppModelController appModelController = new AppModelController();
        ReflectionTestUtils.setField(appModelController, "appPortalService", appPortalService);

        AppChatController appChatController = new AppChatController();
        ReflectionTestUtils.setField(appChatController, "appChatService", appChatService);

        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        AuthInterceptor authInterceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(authInterceptor, "authService", authService);

        publicMockMvc = MockMvcBuilders.standaloneSetup(appAuthController)
                .setControllerAdvice(exceptionHandler)
                .build();

        protectedMockMvc = MockMvcBuilders.standaloneSetup(
                        appAuthController,
                        appKnowledgeBaseController,
                        appModelController,
                        appChatController
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

    @Test
    void shouldCreateAppChatSessionWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(appChatService.createSession(any(), any())).thenReturn(new AppChatSessionResponse(
                21L,
                null,
                "GENERAL",
                "今天的工作梳理",
                1L,
                false,
                List.of(11L, 12L),
                "ENABLED"
        ));

        protectedMockMvc.perform(post("/api/app/chat/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneType": "GENERAL",
                                  "sessionName": "今天的工作梳理",
                                  "chatModelId": 1,
                                  "webSearchEnabled": false,
                                  "selectedKbIds": [11, 12]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.selectedKbIds[0]").value(11));
    }

    @Test
    void shouldListAppChatSessionsWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(appChatService.listSessions(eq(null), eq("GENERAL"), any(), eq(1L), eq(20L))).thenReturn(new PageResponse<>(
                List.of(new AppChatSessionResponse(
                        22L,
                        null,
                        "GENERAL",
                        "首页会话",
                        1L,
                        false,
                        List.of(),
                        "ENABLED"
                )),
                1,
                20,
                1
        ));

        protectedMockMvc.perform(get("/api/app/chat/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .param("sceneType", "GENERAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.list[0].sessionName").value("首页会话"));
    }

    @Test
    void shouldListAppChatMessagesWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(appChatService.listMessages(eq(22L), any())).thenReturn(List.of(
                new ChatMessageResponse(
                        101L,
                        "今天有哪些待办？",
                        "今天需要完成接口联调。",
                        List.of(),
                        null,
                        null
                )
        ));

        protectedMockMvc.perform(get("/api/app/chat/sessions/22/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].messageId").value(101))
                .andExpect(jsonPath("$.data[0].answer").value("今天需要完成接口联调。"));
    }

    @Test
    void shouldUpdateAppSessionKnowledgeBasesWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(appChatService.updateSessionKnowledgeBases(eq(22L), eq(List.of(11L, 13L)), any())).thenReturn(new AppChatSessionResponse(
                22L,
                null,
                "GENERAL",
                "首页会话",
                1L,
                true,
                List.of(11L, 13L),
                "ENABLED"
        ));

        protectedMockMvc.perform(put("/api/app/chat/sessions/22/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selectedKbIds": [11, 13]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.webSearchEnabled").value(true))
                .andExpect(jsonPath("$.data.selectedKbIds[1]").value(13));
    }

    @Test
    void shouldChatThroughAppEndpointWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(appChatService.chat(eq(22L), any(), any())).thenReturn(new ChatResponse(
                102L,
                "根据已选知识库，发布前需要完成回归测试。",
                List.of(),
                new ChatUsageResponse(180, 42)
        ));

        protectedMockMvc.perform(post("/api/app/chat/sessions/22/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AppChatPayload(
                                "发布前需要检查什么？",
                                1L,
                                List.of(11L, 13L),
                                false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.messageId").value(102))
                .andExpect(jsonPath("$.data.usage.promptTokens").value(180));
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser()
                .setUserId(2L)
                .setUsername("app-user")
                .setSessionId("session-app");
    }

    private record AppChatPayload(
            String question,
            Long chatModelId,
            List<Long> selectedKbIds,
            Boolean webSearchEnabled
    ) {
    }
}
