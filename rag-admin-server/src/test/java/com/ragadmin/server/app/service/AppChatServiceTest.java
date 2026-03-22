package com.ragadmin.server.app.service;

import com.ragadmin.server.app.dto.AppChatRequest;
import com.ragadmin.server.app.dto.AppChatSessionResponse;
import com.ragadmin.server.app.dto.AppCreateChatSessionRequest;
import com.ragadmin.server.app.dto.AppUpdateChatSessionRequest;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.ChatTerminalTypes;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
import com.ragadmin.server.chat.dto.ChatStreamEventResponse;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadata;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadataGenerationService;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatFeedbackEntity;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.entity.ChatSessionKnowledgeBaseRelEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatFeedbackMapper;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionKnowledgeBaseRelMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.chat.service.ChatExchangePersistenceService;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatExecutionPlan;
import com.ragadmin.server.infra.ai.chat.ChatExecutionPlanningRequest;
import com.ragadmin.server.infra.ai.chat.ChatExecutionPlanningService;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.ConversationIdCodec;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryManager;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryRefreshDispatcher;
import com.ragadmin.server.infra.search.NoopWebSearchProvider;
import com.ragadmin.server.infra.search.WebSearchProvider;
import com.ragadmin.server.infra.search.WebSearchSnippet;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.service.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AppChatServiceTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatSessionKnowledgeBaseRelMapper chatSessionKnowledgeBaseRelMapper;

    @Mock
    private ChatAnswerReferenceMapper chatAnswerReferenceMapper;

    @Mock
    private ChatFeedbackMapper chatFeedbackMapper;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private ModelService modelService;

    @Mock
    private ConversationChatClient conversationChatClient;

    @Mock
    private ChatExecutionPlanningService chatExecutionPlanningService;

    @Mock
    private ChatAnswerMetadataGenerationService chatAnswerMetadataGenerationService;

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    @Mock
    private ConversationMemoryRefreshDispatcher conversationMemoryRefreshDispatcher;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private ChatExchangePersistenceService chatExchangePersistenceService;

    @Mock
    private WebSearchProvider webSearchProvider;

    @Spy
    private ConversationIdCodec conversationIdCodec = new ConversationIdCodec();

    @InjectMocks
    private AppChatService appChatService;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            ChatExecutionPlanningRequest planningRequest = invocation.getArgument(0);
            boolean needRetrieval = planningRequest.retrievalAvailable();
            boolean needWebSearch = planningRequest.webSearchAvailable();
            return new ChatExecutionPlan(
                    needRetrieval && needWebSearch ? "KNOWLEDGE_BASE_AND_WEB_SEARCH"
                            : (needRetrieval ? "KNOWLEDGE_BASE_QA" : (needWebSearch ? "WEB_SEARCH_QA" : "GENERAL_QA")),
                    needRetrieval,
                    needRetrieval ? planningRequest.question() : "",
                    needWebSearch,
                    needWebSearch ? planningRequest.question() : "",
                    "测试默认规则规划",
                    "RULE_BASED"
            );
        }).when(chatExecutionPlanningService).plan(any());
        lenient().when(chatAnswerMetadataGenerationService.generate(any()))
                .thenReturn(new ChatAnswerMetadata("LOW", false, false));
    }

    @Test
    void shouldFallbackToNoopWebSearchProviderByDefault() {
        AppChatService service = new AppChatService();

        Object provider = ReflectionTestUtils.getField(service, "webSearchProvider");

        assertTrue(provider instanceof NoopWebSearchProvider);
    }

    @Test
    void shouldCreateIndependentGeneralSessionsForSameUserInAppPortal() {
        AtomicLong idGenerator = new AtomicLong(101L);
        when(chatSessionMapper.insert(any(ChatSessionEntity.class))).thenAnswer(invocation -> {
            ChatSessionEntity entity = invocation.getArgument(0);
            entity.setId(idGenerator.getAndIncrement());
            return 1;
        });

        AppCreateChatSessionRequest firstRequest = new AppCreateChatSessionRequest();
        firstRequest.setSceneType(ChatSceneTypes.GENERAL);
        firstRequest.setSessionName("首页会话-1");

        AppCreateChatSessionRequest secondRequest = new AppCreateChatSessionRequest();
        secondRequest.setSceneType(ChatSceneTypes.GENERAL);
        secondRequest.setSessionName("首页会话-2");

        AppChatSessionResponse first = appChatService.createSession(firstRequest, user(2001L));
        AppChatSessionResponse second = appChatService.createSession(secondRequest, user(2001L));

        ArgumentCaptor<ChatSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        verify(chatSessionMapper, times(2)).insert(sessionCaptor.capture());
        List<ChatSessionEntity> insertedSessions = sessionCaptor.getAllValues();

        assertEquals(101L, first.id());
        assertEquals(102L, second.id());
        assertEquals(ChatSceneTypes.GENERAL, first.sceneType());
        assertEquals(ChatSceneTypes.GENERAL, second.sceneType());
        assertIterableEquals(List.of(), first.selectedKbIds());
        assertIterableEquals(List.of(), second.selectedKbIds());

        assertEquals(ChatTerminalTypes.APP, insertedSessions.get(0).getTerminalType());
        assertEquals(ChatTerminalTypes.APP, insertedSessions.get(1).getTerminalType());
        assertEquals("首页会话-1", insertedSessions.get(0).getSessionName());
        assertEquals("首页会话-2", insertedSessions.get(1).getSessionName());
        verify(chatSessionKnowledgeBaseRelMapper, never()).insert(any(ChatSessionKnowledgeBaseRelEntity.class));
    }

    @Test
    void shouldBindAnchorKnowledgeBaseWhenCreateKnowledgeBaseSceneSession() {
        AtomicLong idGenerator = new AtomicLong(201L);
        when(chatSessionMapper.insert(any(ChatSessionEntity.class))).thenAnswer(invocation -> {
            ChatSessionEntity entity = invocation.getArgument(0);
            entity.setId(idGenerator.getAndIncrement());
            return 1;
        });

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(88L);
        when(knowledgeBaseService.requireById(88L)).thenReturn(knowledgeBase);

        AppCreateChatSessionRequest request = new AppCreateChatSessionRequest();
        request.setKbId(88L);
        request.setSessionName("知识库会话");

        AppChatSessionResponse response = appChatService.createSession(request, user(3001L));

        ArgumentCaptor<ChatSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        verify(chatSessionMapper).insert(sessionCaptor.capture());
        ChatSessionEntity insertedSession = sessionCaptor.getValue();
        assertEquals(ChatSceneTypes.KNOWLEDGE_BASE, insertedSession.getSceneType());
        assertEquals(88L, insertedSession.getKbId());
        assertEquals(ChatTerminalTypes.APP, insertedSession.getTerminalType());

        ArgumentCaptor<ChatSessionKnowledgeBaseRelEntity> relCaptor = ArgumentCaptor.forClass(ChatSessionKnowledgeBaseRelEntity.class);
        verify(chatSessionKnowledgeBaseRelMapper).insert(relCaptor.capture());
        ChatSessionKnowledgeBaseRelEntity insertedRel = relCaptor.getValue();
        assertEquals(201L, insertedRel.getSessionId());
        assertEquals(88L, insertedRel.getKbId());
        assertEquals(1, insertedRel.getSortNo());

        assertEquals(201L, response.id());
        assertIterableEquals(List.of(88L), response.selectedKbIds());
    }

    @Test
    void shouldUpdateOwnedAppSessionMetadataAndKeepSelection() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(301L);
        session.setUserId(4001L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSessionName("旧名称");
        session.setModelId(7L);
        session.setWebSearchEnabled(Boolean.FALSE);
        session.setStatus("ENABLED");

        ChatSessionKnowledgeBaseRelEntity rel = new ChatSessionKnowledgeBaseRelEntity();
        rel.setSessionId(301L);
        rel.setKbId(66L);
        rel.setSortNo(1);

        AppUpdateChatSessionRequest request = new AppUpdateChatSessionRequest();
        request.setSessionName("  新名称  ");
        request.setChatModelId(88L);
        request.setWebSearchEnabled(Boolean.TRUE);

        when(chatSessionMapper.selectById(301L)).thenReturn(session);
        when(chatSessionKnowledgeBaseRelMapper.selectList(any())).thenReturn(List.of(rel));
        when(modelService.resolveChatModelDescriptor(88L)).thenReturn(new ModelService.ChatModelDescriptor(
                88L,
                "qwen-plus",
                "BAILIAN",
                "百炼"
        ));

        AppChatSessionResponse response = appChatService.updateSession(301L, request, user(4001L));

        ArgumentCaptor<ChatSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        verify(chatSessionMapper).updateById(sessionCaptor.capture());
        assertEquals("新名称", sessionCaptor.getValue().getSessionName());
        assertEquals(88L, sessionCaptor.getValue().getModelId());
        assertTrue(Boolean.TRUE.equals(sessionCaptor.getValue().getWebSearchEnabled()));
        assertEquals("新名称", response.sessionName());
        assertEquals(88L, response.chatModelId());
        assertTrue(response.webSearchEnabled());
        assertIterableEquals(List.of(66L), response.selectedKbIds());
    }

    @Test
    void shouldClearExplicitChatModelWhenSessionFallsBackToDefault() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(302L);
        session.setUserId(4002L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSessionName("首页会话");
        session.setModelId(99L);
        session.setWebSearchEnabled(Boolean.TRUE);
        session.setStatus("ENABLED");

        AppUpdateChatSessionRequest request = new AppUpdateChatSessionRequest();
        request.setSessionName("首页会话");
        request.setChatModelId(null);
        request.setWebSearchEnabled(Boolean.FALSE);

        when(chatSessionMapper.selectById(302L)).thenReturn(session);
        when(chatSessionKnowledgeBaseRelMapper.selectList(any())).thenReturn(List.of());

        AppChatSessionResponse response = appChatService.updateSession(302L, request, user(4002L));

        ArgumentCaptor<ChatSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        verify(chatSessionMapper).updateById(sessionCaptor.capture());
        assertEquals(null, sessionCaptor.getValue().getModelId());
        assertTrue(Boolean.FALSE.equals(sessionCaptor.getValue().getWebSearchEnabled()));
        assertEquals(null, response.chatModelId());
        assertTrue(!response.webSearchEnabled());
    }

    @Test
    void shouldDeleteOwnedAppSessionWithMessagesAndConversationMemory() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(401L);
        session.setUserId(5001L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setStatus("ENABLED");

        ChatMessageEntity message1 = new ChatMessageEntity();
        message1.setId(701L);
        ChatMessageEntity message2 = new ChatMessageEntity();
        message2.setId(702L);

        when(chatSessionMapper.selectById(401L)).thenReturn(session);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(message1, message2));

        appChatService.deleteSession(401L, user(5001L));

        verify(chatAnswerReferenceMapper).delete(any());
        verify(chatFeedbackMapper).delete(any());
        verify(chatMessageMapper).delete(any());
        verify(chatSessionKnowledgeBaseRelMapper).delete(any());
        verify(conversationMemoryManager).clear("chat-terminal-app-scene-general-user-5001-session-401");
        verify(chatSessionMapper).deleteById(401L);
    }

    @Test
    void shouldIncludePersistedMetadataWhenListingMessages() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(450L);
        session.setUserId(5002L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setStatus("ENABLED");

        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(801L);
        message.setSessionId(450L);
        message.setQuestionText("今天有哪些待办？");
        message.setAnswerText("今天需要完成接口联调。");
        message.setAnswerConfidence("LOW");
        message.setHasKnowledgeBaseEvidence(Boolean.FALSE);
        message.setNeedFollowUp(Boolean.TRUE);

        when(chatSessionMapper.selectById(450L)).thenReturn(session);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(message));
        when(chatAnswerReferenceMapper.selectList(any())).thenReturn(List.of());
        when(chunkMapper.selectBatchIds(List.of())).thenReturn(List.of());
        when(chatFeedbackMapper.selectList(any())).thenReturn(List.of());

        List<ChatMessageResponse> messages = appChatService.listMessages(450L, user(5002L));

        assertEquals(1, messages.size());
        assertNotNull(messages.getFirst().metadata());
        assertEquals("LOW", messages.getFirst().metadata().confidence());
        assertEquals(false, messages.getFirst().metadata().hasKnowledgeBaseEvidence());
        assertEquals(true, messages.getFirst().metadata().needFollowUp());
    }

    @Test
    void shouldIgnoreWebSearchFailureAndUseRequestedModelWhenChatting() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(501L);
        session.setUserId(6001L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSessionName("首页会话");
        session.setStatus("ENABLED");

        AppChatRequest request = new AppChatRequest();
        request.setQuestion("今天适合安排哪些工作？");
        request.setChatModelId(901L);
        request.setWebSearchEnabled(Boolean.TRUE);

        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                901L,
                "qwen-plus",
                "BAILIAN",
                "百炼"
        );
        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(List.of(), "");

        when(chatSessionMapper.selectById(501L)).thenReturn(session);
        when(chatSessionKnowledgeBaseRelMapper.selectList(any())).thenReturn(List.of());
        when(webSearchProvider.search("今天适合安排哪些工作？", 5)).thenThrow(new RuntimeException("search down"));
        when(modelService.resolveChatModelDescriptor(901L)).thenReturn(modelDescriptor);
        when(conversationChatClient.chat(eq("BAILIAN"), eq("qwen-plus"), any(), any(), any()))
                .thenReturn(new ChatCompletionResult("建议先处理高优先级事项。", 80, 20));
        ChatAnswerMetadata metadata = new ChatAnswerMetadata("LOW", false, false);
        when(chatAnswerMetadataGenerationService.generate(any())).thenReturn(metadata);
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(6001L),
                eq("今天适合安排哪些工作？"),
                eq("建议先处理高优先级事项。"),
                eq(901L),
                eq(80),
                eq(20),
                anyInt(),
                eq(metadata),
                eq(retrievalResult)
        )).thenReturn(new com.ragadmin.server.chat.dto.ChatResponse(
                1001L,
                "建议先处理高优先级事项。",
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(80, 20),
                new com.ragadmin.server.chat.dto.ChatAnswerMetadataResponse("LOW", false, false)
        ));

        com.ragadmin.server.chat.dto.ChatResponse response = appChatService.chat(501L, request, user(6001L));

        ArgumentCaptor<List<ChatPromptMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(conversationChatClient).chat(eq("BAILIAN"), eq("qwen-plus"), any(), promptCaptor.capture(), any());
        List<ChatPromptMessage> promptMessages = promptCaptor.getValue();
        assertEquals(2, promptMessages.size());
        assertEquals("今天适合安排哪些工作？", promptMessages.get(1).content());
        verify(modelService).resolveChatModelDescriptor(901L);
        verify(conversationMemoryRefreshDispatcher).dispatchRefresh("chat-terminal-app-scene-general-user-6001-session-501");
        assertEquals("建议先处理高优先级事项。", response.answer());
        assertEquals("LOW", response.metadata().confidence());
    }

    @Test
    void shouldAppendWebSearchContextToPromptWhenEnabled() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(502L);
        session.setUserId(6002L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSessionName("首页会话");
        session.setStatus("ENABLED");

        AppChatRequest request = new AppChatRequest();
        request.setQuestion("今天有什么行业动态？");
        request.setChatModelId(902L);
        request.setWebSearchEnabled(Boolean.TRUE);

        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                902L,
                "qwen-max",
                "BAILIAN",
                "百炼"
        );
        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(List.of(), "");

        when(chatSessionMapper.selectById(502L)).thenReturn(session);
        when(chatSessionKnowledgeBaseRelMapper.selectList(any())).thenReturn(List.of());
        when(webSearchProvider.search("今天有什么行业动态？", 5)).thenReturn(List.of(
                new WebSearchSnippet(
                        "行业快讯",
                        "多家企业正在推进智能体应用落地。",
                        "https://example.com/news",
                        Instant.parse("2026-03-20T10:00:00Z")
                )
        ));
        when(modelService.resolveChatModelDescriptor(902L)).thenReturn(modelDescriptor);
        when(conversationChatClient.chat(eq("BAILIAN"), eq("qwen-max"), any(), any(), any()))
                .thenReturn(new ChatCompletionResult("可以重点关注智能体应用落地。", 96, 26));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(6002L),
                eq("今天有什么行业动态？"),
                eq("可以重点关注智能体应用落地。"),
                eq(902L),
                eq(96),
                eq(26),
                anyInt(),
                any(),
                eq(retrievalResult)
        )).thenReturn(new com.ragadmin.server.chat.dto.ChatResponse(
                1002L,
                "可以重点关注智能体应用落地。",
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(96, 26),
                null
        ));

        appChatService.chat(502L, request, user(6002L));

        ArgumentCaptor<List<ChatPromptMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(conversationChatClient).chat(eq("BAILIAN"), eq("qwen-max"), any(), promptCaptor.capture(), any());
        List<ChatPromptMessage> promptMessages = promptCaptor.getValue();
        assertEquals(2, promptMessages.size());
        assertTrue(promptMessages.get(1).content().contains("联网搜索摘要"));
        assertTrue(promptMessages.get(1).content().contains("行业快讯"));
        assertTrue(promptMessages.get(1).content().contains("https://example.com/news"));
    }

    @Test
    void shouldUseStructuredPlanQueriesForRetrievalAndWebSearch() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(503L);
        session.setUserId(6003L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSessionName("首页会话");
        session.setStatus("ENABLED");

        ChatSessionKnowledgeBaseRelEntity rel = new ChatSessionKnowledgeBaseRelEntity();
        rel.setSessionId(503L);
        rel.setKbId(88L);
        rel.setSortNo(1);

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(88L);
        knowledgeBase.setChatModelId(903L);

        AppChatRequest request = new AppChatRequest();
        request.setQuestion("最近智能体应用有什么值得关注的实践？");
        request.setWebSearchEnabled(Boolean.TRUE);

        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                903L,
                "qwen-max",
                "BAILIAN",
                "百炼"
        );
        com.ragadmin.server.document.entity.ChunkEntity chunk = new com.ragadmin.server.document.entity.ChunkEntity();
        chunk.setId(7001L);
        chunk.setKbId(88L);
        chunk.setDocumentId(9901L);
        chunk.setChunkNo(1);
        chunk.setChunkText("智能体落地应先从高频、低风险场景切入。");
        RetrievalService.RetrievedChunk retrievedChunk = new RetrievalService.RetrievedChunk(chunk, 0.88D);
        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(
                List.of(retrievedChunk),
                "片段1:\n智能体落地应先从高频、低风险场景切入。"
        );

        when(chatSessionMapper.selectById(503L)).thenReturn(session);
        when(chatSessionKnowledgeBaseRelMapper.selectList(any())).thenReturn(List.of(rel));
        when(knowledgeBaseService.requireById(88L)).thenReturn(knowledgeBase);
        when(modelService.resolveChatModelDescriptor(903L)).thenReturn(modelDescriptor);
        doReturn(new ChatExecutionPlan(
                "KNOWLEDGE_BASE_AND_WEB_SEARCH",
                true,
                "智能体应用落地 最佳实践",
                true,
                "智能体 行业动态",
                "优先使用知识库并补充联网动态",
                "MODEL"
        )).when(chatExecutionPlanningService).plan(any());
        when(webSearchProvider.search("智能体 行业动态", 5)).thenReturn(List.of(
                new WebSearchSnippet("行业快讯", "近期多家企业推进智能体落地。", "https://example.com/agent-news", Instant.parse("2026-03-20T10:00:00Z"))
        ));
        when(retrievalService.retrieveAcrossKnowledgeBases(List.of(knowledgeBase), "智能体应用落地 最佳实践"))
                .thenReturn(retrievalResult);
        when(conversationChatClient.chat(eq("BAILIAN"), eq("qwen-max"), any(), any(), any()))
                .thenReturn(new ChatCompletionResult("建议优先选择高频低风险场景，并关注近期行业落地案例。", 120, 40));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(6003L),
                eq("最近智能体应用有什么值得关注的实践？"),
                eq("建议优先选择高频低风险场景，并关注近期行业落地案例。"),
                eq(903L),
                eq(120),
                eq(40),
                anyInt(),
                any(),
                eq(retrievalResult)
        )).thenReturn(new com.ragadmin.server.chat.dto.ChatResponse(
                1003L,
                "建议优先选择高频低风险场景，并关注近期行业落地案例。",
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(120, 40),
                null
        ));

        appChatService.chat(503L, request, user(6003L));

        verify(webSearchProvider).search("智能体 行业动态", 5);
        verify(retrievalService).retrieveAcrossKnowledgeBases(List.of(knowledgeBase), "智能体应用落地 最佳实践");
    }

    @Test
    void shouldReturnErrorEventWhenStreamPreparationFailed() {
        AppChatRequest request = new AppChatRequest();
        request.setQuestion("会话还在吗？");

        List<ChatStreamEventResponse> events = appChatService.streamChat(999L, request, user(7001L))
                .collectList()
                .block();

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("ERROR", events.getFirst().eventType());
        assertEquals("会话不存在", events.getFirst().errorMessage());
    }

    @Test
    void shouldIgnoreEmptyStreamChunkAndStillCompleteInAppChat() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(601L);
        session.setUserId(8001L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSessionName("首页会话");
        session.setStatus("ENABLED");

        AppChatRequest request = new AppChatRequest();
        request.setQuestion("帮我总结一下");

        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                901L,
                "qwen-max",
                "BAILIAN",
                "百炼"
        );
        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(List.of(), "");

        when(chatSessionMapper.selectById(601L)).thenReturn(session);
        when(chatSessionKnowledgeBaseRelMapper.selectList(any())).thenReturn(List.of());
        when(modelService.resolveChatModelDescriptor(null)).thenReturn(modelDescriptor);
        when(conversationChatClient.stream(eq("BAILIAN"), eq("qwen-max"), any(), any(), any()))
                .thenReturn(Flux.just(
                        chatChunk("先给出结论。", 30, 5),
                        chatChunk("", 30, 18)
                ));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(8001L),
                eq("帮我总结一下"),
                eq("先给出结论。"),
                eq(901L),
                eq(30),
                eq(18),
                anyInt(),
                any(),
                eq(retrievalResult)
        )).thenReturn(new com.ragadmin.server.chat.dto.ChatResponse(
                3001L,
                "先给出结论。",
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(30, 18),
                null
        ));

        List<ChatStreamEventResponse> events = appChatService.streamChat(601L, request, user(8001L))
                .collectList()
                .block();

        assertNotNull(events);
        assertEquals(2, events.size());
        assertEquals("DELTA", events.get(0).eventType());
        assertEquals("先给出结论。", events.get(0).delta());
        assertEquals("COMPLETE", events.get(1).eventType());
        assertEquals(3001L, events.get(1).messageId());
    }

    private org.springframework.ai.chat.model.ChatResponse chatChunk(String text, int promptTokens, int completionTokens) {
        return org.springframework.ai.chat.model.ChatResponse.builder()
                .metadata(org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
                        .usage(new org.springframework.ai.chat.metadata.Usage() {
                            @Override
                            public Integer getPromptTokens() {
                                return promptTokens;
                            }

                            @Override
                            public Integer getCompletionTokens() {
                                return completionTokens;
                            }

                            @Override
                            public Object getNativeUsage() {
                                return null;
                            }
                        })
                        .build())
                .generations(List.of(new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage(text)
                )))
                .build();
    }

    private AuthenticatedUser user(Long userId) {
        return new AuthenticatedUser()
                .setUserId(userId)
                .setUsername("tester")
                .setSessionId("session-" + userId);
    }
}
