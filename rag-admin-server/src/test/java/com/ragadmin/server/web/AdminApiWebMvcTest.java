package com.ragadmin.server.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragadmin.server.auth.controller.AuthController;
import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.dto.RefreshTokenResponse;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthInterceptor;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.chat.controller.ChatController;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatUsageResponse;
import com.ragadmin.server.chat.service.ChatService;
import com.ragadmin.server.common.exception.GlobalExceptionHandler;
import com.ragadmin.server.task.controller.TaskController;
import com.ragadmin.server.task.dto.TaskDetailResponse;
import com.ragadmin.server.task.dto.TaskRetryRecordResponse;
import com.ragadmin.server.task.dto.TaskStepResponse;
import com.ragadmin.server.task.service.TaskService;
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

import java.time.LocalDateTime;
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
class AdminApiWebMvcTest {

    @Mock
    private AuthService authService;

    @Mock
    private TaskService taskService;

    @Mock
    private ChatService chatService;

    private MockMvc publicMockMvc;
    private MockMvc protectedMockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController();
        ReflectionTestUtils.setField(authController, "authService", authService);

        TaskController taskController = new TaskController();
        ReflectionTestUtils.setField(taskController, "taskService", taskService);

        ChatController chatController = new ChatController();
        ReflectionTestUtils.setField(chatController, "chatService", chatService);

        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        AuthInterceptor authInterceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(authInterceptor, "authService", authService);

        publicMockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(exceptionHandler)
                .build();

        protectedMockMvc = MockMvcBuilders.standaloneSetup(taskController, chatController)
                .addInterceptors(authInterceptor)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void shouldAllowLoginWithoutBearerToken() throws Exception {
        CurrentUserResponse currentUser = new CurrentUserResponse()
                .setId(1L)
                .setUsername("admin")
                .setDisplayName("系统管理员")
                .setRoles(List.of("ADMIN"));

        LoginResponse response = new LoginResponse()
                .setAccessToken("access-token")
                .setRefreshToken("refresh-token")
                .setExpiresIn(7200)
                .setRefreshExpiresIn(604800)
                .setUser(currentUser);

        when(authService.login(any())).thenReturn(response);

        publicMockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "admin",
                                  "password": "Admin@123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.user.username").value("admin"));

        verify(authService, never()).authenticateAccessToken(any());
    }

    @Test
    void shouldAllowRefreshWithoutBearerToken() throws Exception {
        RefreshTokenResponse response = new RefreshTokenResponse()
                .setAccessToken("new-access-token")
                .setRefreshToken("new-refresh-token")
                .setExpiresIn(7200)
                .setRefreshExpiresIn(604800);

        when(authService.refresh("old-refresh-token")).thenReturn(response);

        publicMockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "old-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void shouldRejectProtectedTaskApiWithoutBearerToken() throws Exception {
        protectedMockMvc.perform(get("/api/admin/tasks/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("缺少有效的 Bearer Token"));
    }

    @Test
    void shouldReturnTaskDetailWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(taskService.detail(1L)).thenReturn(new TaskDetailResponse(
                1L,
                "DOCUMENT_PARSE",
                "SUCCESS",
                "DOCUMENT",
                10L,
                20L,
                10L,
                30L,
                "员工手册.md",
                "SUCCESS",
                1,
                null,
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 10, 1),
                LocalDateTime.of(2026, 3, 10, 9, 59),
                LocalDateTime.of(2026, 3, 10, 10, 1),
                List.of(new TaskStepResponse("EXTRACT_TEXT", "文本抽取", "SUCCESS", null, null, null)),
                List.of(new TaskRetryRecordResponse(1, "手动重试", "SUBMITTED", LocalDateTime.of(2026, 3, 10, 9, 58)))
        ));

        protectedMockMvc.perform(get("/api/admin/tasks/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value(1))
                .andExpect(jsonPath("$.data.documentName").value("员工手册.md"))
                .andExpect(jsonPath("$.data.steps[0].stepCode").value("EXTRACT_TEXT"))
                .andExpect(jsonPath("$.data.retryRecords[0].retryResult").value("SUBMITTED"));
    }

    @Test
    void shouldReturnChatResponseWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token")).thenReturn(authenticatedUser());
        when(chatService.chat(eq(11L), any(), any())).thenReturn(new ChatResponse(
                101L,
                "这是知识库回答",
                List.of(),
                new ChatUsageResponse(120, 30)
        ));

        protectedMockMvc.perform(post("/api/admin/chat/sessions/11/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequestPayload("总结文档内容", 20L, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.messageId").value(101))
                .andExpect(jsonPath("$.data.answer").value("这是知识库回答"))
                .andExpect(jsonPath("$.data.usage.promptTokens").value(120));
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser()
                .setUserId(1L)
                .setUsername("admin")
                .setSessionId("session-1");
    }

    private record ChatRequestPayload(String question, Long kbId, Boolean stream) {
    }
}
