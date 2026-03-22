package com.ragadmin.server.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragadmin.server.auth.controller.AuthController;
import com.ragadmin.server.auth.controller.UserController;
import com.ragadmin.server.auth.controller.UserSessionController;
import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.dto.RefreshTokenResponse;
import com.ragadmin.server.auth.dto.UserSessionListItemResponse;
import com.ragadmin.server.auth.dto.UserListItemResponse;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthInterceptor;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.auth.service.UserAdminService;
import com.ragadmin.server.auth.service.UserSessionAdminService;
import com.ragadmin.server.common.exception.GlobalExceptionHandler;
import com.ragadmin.server.document.controller.DocumentController;
import com.ragadmin.server.document.controller.FileController;
import com.ragadmin.server.document.dto.DocumentUploadCapabilityResponse;
import com.ragadmin.server.document.dto.DocumentResponse;
import com.ragadmin.server.document.dto.UploadUrlResponse;
import com.ragadmin.server.document.service.DocumentService;
import com.ragadmin.server.document.service.FileUploadService;
import com.ragadmin.server.knowledge.controller.KnowledgeBaseController;
import com.ragadmin.server.knowledge.dto.KnowledgeBaseResponse;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.model.controller.ModelController;
import com.ragadmin.server.model.controller.ModelProviderController;
import com.ragadmin.server.model.dto.ModelHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelProviderHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelResponse;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.model.service.ModelProviderService;
import com.ragadmin.server.system.controller.SystemHealthController;
import com.ragadmin.server.system.dto.DependencyHealthResponse;
import com.ragadmin.server.system.dto.HealthCheckResponse;
import com.ragadmin.server.system.service.SystemHealthService;
import com.ragadmin.server.task.controller.TaskController;
import com.ragadmin.server.task.controller.TaskEventController;
import com.ragadmin.server.task.dto.TaskDetailResponse;
import com.ragadmin.server.task.dto.TaskRealtimeEventResponse;
import com.ragadmin.server.task.dto.TaskRetryRecordResponse;
import com.ragadmin.server.task.dto.TaskStepResponse;
import com.ragadmin.server.task.service.TaskRealtimeEventService;
import com.ragadmin.server.task.service.TaskService;
import com.ragadmin.server.statistics.controller.StatisticsController;
import com.ragadmin.server.statistics.dto.VectorIndexOverviewResponse;
import com.ragadmin.server.statistics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminApiWebMvcTest {

    @Mock
    private AuthService authService;

    @Mock
    private TaskService taskService;

    @Mock
    private TaskRealtimeEventService taskRealtimeEventService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private DocumentService documentService;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private SystemHealthService systemHealthService;

    @Mock
    private ModelService modelService;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private UserAdminService userAdminService;

    @Mock
    private UserSessionAdminService userSessionAdminService;

    private MockMvc publicMockMvc;
    private MockMvc protectedMockMvc;
    private TestWebMvcContextSupport.ManagedMockMvc publicContext;
    private TestWebMvcContextSupport.ManagedMockMvc protectedContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController();
        ReflectionTestUtils.setField(authController, "authService", authService);

        TaskController taskController = new TaskController();
        ReflectionTestUtils.setField(taskController, "taskService", taskService);

        TaskEventController taskEventController = new TaskEventController();
        ReflectionTestUtils.setField(taskEventController, "taskRealtimeEventService", taskRealtimeEventService);

        KnowledgeBaseController knowledgeBaseController = new KnowledgeBaseController();
        ReflectionTestUtils.setField(knowledgeBaseController, "knowledgeBaseService", knowledgeBaseService);
        ReflectionTestUtils.setField(knowledgeBaseController, "documentService", documentService);

        DocumentController documentController = new DocumentController();
        ReflectionTestUtils.setField(documentController, "documentService", documentService);

        FileController fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "fileUploadService", fileUploadService);

        SystemHealthController systemHealthController = new SystemHealthController();
        ReflectionTestUtils.setField(systemHealthController, "systemHealthService", systemHealthService);

        ModelController modelController = new ModelController();
        ReflectionTestUtils.setField(modelController, "modelService", modelService);

        ModelProviderController modelProviderController = new ModelProviderController();
        ReflectionTestUtils.setField(modelProviderController, "modelProviderService", modelProviderService);

        StatisticsController statisticsController = new StatisticsController();
        ReflectionTestUtils.setField(statisticsController, "statisticsService", statisticsService);

        UserController userController = new UserController();
        ReflectionTestUtils.setField(userController, "userAdminService", userAdminService);

        UserSessionController userSessionController = new UserSessionController();
        ReflectionTestUtils.setField(userSessionController, "userSessionAdminService", userSessionAdminService);

        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        AuthInterceptor authInterceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(authInterceptor, "authService", authService);

        publicContext = TestWebMvcContextSupport.create(
                List.of(authController),
                List.of(exceptionHandler),
                List.of()
        );
        publicMockMvc = publicContext.mockMvc();

        protectedContext = TestWebMvcContextSupport.create(
                List.of(
                        taskController,
                        taskEventController,
                        knowledgeBaseController,
                        documentController,
                        fileController,
                        systemHealthController,
                        modelController,
                        modelProviderController,
                        statisticsController,
                        userController,
                        userSessionController
                ),
                List.of(exceptionHandler),
                List.of(authInterceptor)
        );
        protectedMockMvc = protectedContext.mockMvc();
    }

    @AfterEach
    void tearDown() {
        if (publicContext != null) {
            publicContext.close();
        }
        if (protectedContext != null) {
            protectedContext.close();
        }
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

        when(authService.loginForAdminPortal(any())).thenReturn(response);

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

        verify(authService, never()).authenticateAccessToken(any(), any());
    }

    @Test
    void shouldAllowRefreshWithoutBearerToken() throws Exception {
        RefreshTokenResponse response = new RefreshTokenResponse()
                .setAccessToken("new-access-token")
                .setRefreshToken("new-refresh-token")
                .setExpiresIn(7200)
                .setRefreshExpiresIn(604800);

        when(authService.refreshForAdminPortal("old-refresh-token")).thenReturn(response);

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
    void shouldStreamTaskEventsThroughAdminEndpointWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(taskRealtimeEventService.subscribeTasks()).thenReturn(Flux.just(
                new TaskRealtimeEventResponse(
                        "CONNECTED",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        "任务实时通道已连接",
                        false,
                        LocalDateTime.of(2026, 3, 22, 9, 10)
                ),
                new TaskRealtimeEventResponse(
                        "TASK_STARTED",
                        101L,
                        11L,
                        21L,
                        "员工手册.pdf",
                        "RUNNING",
                        "PROCESSING",
                        "EXTRACT_TEXT",
                        "文本抽取",
                        20,
                        "解析任务已开始执行",
                        false,
                        LocalDateTime.of(2026, 3, 22, 9, 10, 1)
                )
        ));

        MvcResult mvcResult = protectedMockMvc.perform(get("/api/admin/events/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult streamResult = protectedMockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("\"eventType\":\"CONNECTED\"")))
                .andExpect(content().string(containsString("\"eventType\":\"TASK_STARTED\"")))
                .andReturn();

        String body = streamResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("event:"));
        assertFalse(body.contains("id:101"));
        assertTrue(body.contains("任务实时通道已连接"));
        assertTrue(body.contains("员工手册.pdf"));
    }

    @Test
    void shouldReturnTaskDetailWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
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
    void shouldCreateKnowledgeBaseWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(knowledgeBaseService.create(any(), eq(1L))).thenReturn(new KnowledgeBaseResponse(
                21L,
                "demo-kb",
                "演示知识库",
                "用于测试",
                2L,
                "nomic-embed-text",
                1L,
                "qwen2.5:7b",
                5,
                false,
                "ENABLED"
        ));

        protectedMockMvc.perform(post("/api/admin/knowledge-bases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kbCode": "demo-kb",
                                  "kbName": "演示知识库",
                                  "description": "用于测试",
                                  "embeddingModelId": 2,
                                  "chatModelId": 1,
                                  "retrieveTopK": 5,
                                  "rerankEnabled": false,
                                  "status": "ENABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.kbCode").value("demo-kb"));
    }

    @Test
    void shouldCreateUploadUrlWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(fileUploadService.createUploadUrl(any())).thenReturn(new UploadUrlResponse(
                "ragadmin",
                "kb_document/20260310/demo/sample.md",
                "http://minio/upload"
        ));

        protectedMockMvc.perform(post("/api/admin/files/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "sample.md",
                                  "contentType": "text/markdown",
                                  "bizType": "KB_DOCUMENT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.bucket").value("ragadmin"))
                .andExpect(jsonPath("$.data.objectKey").value("kb_document/20260310/demo/sample.md"));
    }

    @Test
    void shouldReturnUploadCapabilityWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(fileUploadService.getUploadCapability()).thenReturn(new DocumentUploadCapabilityResponse(
                true,
                true,
                "tesseract 5.5.0",
                "chi_sim+eng",
                5,
                List.of("TXT", "PDF", "PNG"),
                List.of("PNG", "JPG", "JPEG", "WEBP")
        ));

        protectedMockMvc.perform(get("/api/admin/files/upload-capability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.ocrEnabled").value(true))
                .andExpect(jsonPath("$.data.ocrAvailable").value(true))
                .andExpect(jsonPath("$.data.ocrLanguage").value("chi_sim+eng"))
                .andExpect(jsonPath("$.data.ocrImageDocTypes[0]").value("PNG"));
    }

    @Test
    void shouldCreateDocumentWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(documentService.createDocument(eq(21L), any(), eq(1L))).thenReturn(new DocumentResponse(
                31L,
                21L,
                "演示知识库",
                "sample.md",
                "MARKDOWN",
                "ragadmin",
                "kb_document/20260310/demo/sample.md",
                1,
                "PENDING",
                true,
                128L,
                "hash-1",
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 10, 10, 1)
        ));

        protectedMockMvc.perform(post("/api/admin/knowledge-bases/21/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "docName": "sample.md",
                                  "docType": "MARKDOWN",
                                  "storageBucket": "ragadmin",
                                  "storageObjectKey": "kb_document/20260310/demo/sample.md",
                                  "fileSize": 128,
                                  "contentHash": "hash-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(31))
                .andExpect(jsonPath("$.data.parseStatus").value("PENDING"));
    }

    @Test
    void shouldReturnKnowledgeBaseDocumentsWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(documentService.listKnowledgeBaseDocuments(21L, null, null, null, 1L, 20L))
                .thenReturn(new PageResponse<>(
                        List.of(new DocumentResponse(
                                31L,
                                21L,
                                "演示知识库",
                                "sample.md",
                                "MARKDOWN",
                                "ragadmin",
                                "kb_document/20260310/demo/sample.md",
                                1,
                                "SUCCESS",
                                true,
                                128L,
                                "hash-1",
                                LocalDateTime.of(2026, 3, 10, 10, 0),
                                LocalDateTime.of(2026, 3, 10, 10, 1)
                        )),
                        1,
                        20,
                        1
                ));

        protectedMockMvc.perform(get("/api/admin/knowledge-bases/21/documents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.list[0].docName").value("sample.md"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldReturnSystemHealthWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(systemHealthService.check()).thenReturn(new HealthCheckResponse(
                "UP",
                new DependencyHealthResponse("UP", "PostgreSQL 正常"),
                new DependencyHealthResponse("UP", "Redis 正常"),
                new DependencyHealthResponse("UP", "MinIO 正常"),
                new DependencyHealthResponse("UP", "百炼正常"),
                new DependencyHealthResponse("UP", "Ollama 正常"),
                new DependencyHealthResponse("UP", "Milvus 正常"),
                new DependencyHealthResponse("UNKNOWN", "OCR 已禁用")
        ));

        protectedMockMvc.perform(get("/api/admin/system/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.postgres.status").value("UP"))
                .andExpect(jsonPath("$.data.bailian.status").value("UP"))
                .andExpect(jsonPath("$.data.milvus.status").value("UP"));
    }

    @Test
    void shouldUpdateModelWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(modelService.update(eq(5L), any())).thenReturn(new ModelResponse(
                5L,
                1L,
                "BAILIAN",
                "阿里百炼",
                "deepseek-v3.2",
                "deepseek-v3.2",
                List.of("TEXT_GENERATION"),
                "CHAT",
                null,
                null,
                "ENABLED",
                false
        ));

        protectedMockMvc.perform(put("/api/admin/models/5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": 1,
                                  "modelCode": "deepseek-v3.2",
                                  "modelName": "deepseek-v3.2",
                                  "capabilityTypes": ["TEXT_GENERATION"],
                                  "modelType": "CHAT",
                                  "status": "ENABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.capabilityTypes[0]").value("TEXT_GENERATION"));
    }

    @Test
    void shouldSetDefaultChatModelWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(modelService.setDefaultChatModel(5L)).thenReturn(new ModelResponse(
                5L,
                1L,
                "BAILIAN",
                "阿里百炼",
                "qwen-max",
                "通义千问 Max",
                List.of("TEXT_GENERATION"),
                "CHAT",
                8000,
                null,
                "ENABLED",
                true
        ));

        protectedMockMvc.perform(post("/api/admin/models/5/default-chat-model")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.isDefaultChatModel").value(true));
    }

    @Test
    void shouldRunModelHealthCheckWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(modelService.healthCheck(5L)).thenReturn(new ModelHealthCheckResponse(
                5L,
                "deepseek-v3.2",
                "BAILIAN",
                "UP",
                "模型探活成功",
                List.of()
        ));

        protectedMockMvc.perform(post("/api/admin/models/5/health-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.modelId").value(5))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void shouldRunModelProviderHealthCheckWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(modelProviderService.healthCheck(1L)).thenReturn(new ModelProviderHealthCheckResponse(
                1L,
                "BAILIAN",
                "阿里百炼",
                "UP",
                "提供方探活成功",
                List.of()
        ));

        protectedMockMvc.perform(post("/api/admin/model-providers/1/health-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.providerId").value(1))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void shouldReturnVectorIndexOverviewWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        VectorIndexOverviewResponse response = new VectorIndexOverviewResponse();
        response.setKbId(21L);
        response.setKbCode("demo-kb");
        response.setKbName("演示知识库");
        response.setKbStatus("ENABLED");
        response.setEmbeddingModelSource("CUSTOM");
        response.setEmbeddingModelCode("text-embedding-v3");
        response.setEmbeddingModelName("text-embedding-v3");
        response.setDocumentCount(6L);
        response.setSuccessDocumentCount(5L);
        response.setChunkCount(148L);
        response.setVectorRefCount(148L);
        response.setCollectionName("kb_21_emb_2_d_1024");
        response.setEmbeddingDim(1024);
        response.setMilvusStatus("UP");
        response.setMilvusMessage("集合已加载，metricType=COSINE");
        when(statisticsService.vectorIndexes(null, null)).thenReturn(List.of(response));

        protectedMockMvc.perform(get("/api/admin/statistics/vector-indexes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].kbId").value(21))
                .andExpect(jsonPath("$.data[0].collectionName").value("kb_21_emb_2_d_1024"))
                .andExpect(jsonPath("$.data[0].milvusStatus").value("UP"));
    }

    @Test
    void shouldDeleteModelWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        doNothing().when(modelService).delete(5L);

        protectedMockMvc.perform(delete("/api/admin/models/5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(modelService).delete(5L);
    }

    @Test
    void shouldDeleteKnowledgeBaseWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        doNothing().when(knowledgeBaseService).delete(21L);

        protectedMockMvc.perform(delete("/api/admin/knowledge-bases/21")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(knowledgeBaseService).delete(21L);
    }

    @Test
    void shouldDeleteDocumentWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        doNothing().when(documentService).delete(31L);

        protectedMockMvc.perform(delete("/api/admin/documents/31")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(documentService).delete(31L);
    }

    @Test
    void shouldReturnUsersWhenAdminHasPermission() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(userAdminService.list(null, null, 1L, 20L)).thenReturn(new PageResponse<>(
                List.of(new UserListItemResponse()
                        .setId(2L)
                        .setUsername("app-user")
                        .setDisplayName("前台用户")
                        .setMobile("13900000000")
                        .setStatus("ENABLED")
                        .setRoles(List.of("APP_USER"))),
                1,
                20,
                1
        ));

        protectedMockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.list[0].username").value("app-user"))
                .andExpect(jsonPath("$.data.list[0].roles[0]").value("APP_USER"));
    }

    @Test
    void shouldReturnUserSessionsWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        when(userSessionAdminService.list(null, "APP_USER", "all", 1L, 20L)).thenReturn(new PageResponse<>(
                List.of(new UserSessionListItemResponse()
                        .setUserId(2L)
                        .setUsername("app-user")
                        .setDisplayName("前台用户")
                        .setRoles(List.of("APP_USER"))
                        .setAdminOnline(false)
                        .setAppOnline(true)),
                1,
                20,
                1
        ));

        protectedMockMvc.perform(get("/api/admin/user-sessions")
                        .queryParam("roleCode", "APP_USER")
                        .queryParam("onlineScope", "all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.list[0].userId").value(2))
                .andExpect(jsonPath("$.data.list[0].username").value("app-user"))
                .andExpect(jsonPath("$.data.list[0].appOnline").value(true));
    }

    @Test
    void shouldKickoutUserSessionWhenBearerTokenIsValid() throws Exception {
        when(authService.authenticateAccessToken("access-token", AuthService.ADMIN_LOGIN_TYPE)).thenReturn(authenticatedUser());
        doNothing().when(userSessionAdminService).kickout(any(), eq(2L), any());

        protectedMockMvc.perform(post("/api/admin/user-sessions/2/kickout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": "all",
                                  "reason": "管理员手动下线"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(userSessionAdminService).kickout(any(), eq(2L), any());
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser()
                .setUserId(1L)
                .setUsername("admin")
                .setSessionId("session-1")
                .setLoginType(AuthService.ADMIN_LOGIN_TYPE)
                .setTokenValue("access-token");
    }
}
