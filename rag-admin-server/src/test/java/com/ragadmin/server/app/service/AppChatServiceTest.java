package com.ragadmin.server.app.service;

import com.ragadmin.server.app.dto.AppChatRequest;
import com.ragadmin.server.app.dto.AppChatSessionResponse;
import com.ragadmin.server.app.dto.AppCreateChatSessionRequest;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.ChatTerminalTypes;
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
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.ConversationIdCodec;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryManager;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryRefreshDispatcher;
import com.ragadmin.server.infra.search.WebSearchProvider;
import com.ragadmin.server.infra.search.WebSearchSnippet;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void shouldRenameOwnedAppSessionAndKeepSelection() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(301L);
        session.setUserId(4001L);
        session.setSceneType(ChatSceneTypes.GENERAL);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSessionName("旧名称");
        session.setStatus("ENABLED");

        ChatSessionKnowledgeBaseRelEntity rel = new ChatSessionKnowledgeBaseRelEntity();
        rel.setSessionId(301L);
        rel.setKbId(66L);
        rel.setSortNo(1);

        when(chatSessionMapper.selectById(301L)).thenReturn(session);
        when(chatSessionKnowledgeBaseRelMapper.selectList(any())).thenReturn(List.of(rel));

        AppChatSessionResponse response = appChatService.renameSession(301L, "  新名称  ", user(4001L));

        ArgumentCaptor<ChatSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        verify(chatSessionMapper).updateById(sessionCaptor.capture());
        assertEquals("新名称", sessionCaptor.getValue().getSessionName());
        assertEquals("新名称", response.sessionName());
        assertIterableEquals(List.of(66L), response.selectedKbIds());
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
                .thenReturn(new ChatModelClient.ChatCompletionResult("建议先处理高优先级事项。", 80, 20));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(6001L),
                eq("今天适合安排哪些工作？"),
                eq("建议先处理高优先级事项。"),
                eq(901L),
                eq(80),
                eq(20),
                anyInt(),
                eq(retrievalResult)
        )).thenReturn(new com.ragadmin.server.chat.dto.ChatResponse(
                1001L,
                "建议先处理高优先级事项。",
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(80, 20)
        ));

        com.ragadmin.server.chat.dto.ChatResponse response = appChatService.chat(501L, request, user(6001L));

        ArgumentCaptor<List<ChatModelClient.ChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(conversationChatClient).chat(eq("BAILIAN"), eq("qwen-plus"), any(), promptCaptor.capture(), any());
        List<ChatModelClient.ChatMessage> promptMessages = promptCaptor.getValue();
        assertEquals(2, promptMessages.size());
        assertEquals("今天适合安排哪些工作？", promptMessages.get(1).content());
        verify(modelService).resolveChatModelDescriptor(901L);
        verify(conversationMemoryRefreshDispatcher).dispatchRefresh("chat-terminal-app-scene-general-user-6001-session-501");
        assertEquals("建议先处理高优先级事项。", response.answer());
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
                .thenReturn(new ChatModelClient.ChatCompletionResult("可以重点关注智能体应用落地。", 96, 26));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(6002L),
                eq("今天有什么行业动态？"),
                eq("可以重点关注智能体应用落地。"),
                eq(902L),
                eq(96),
                eq(26),
                anyInt(),
                eq(retrievalResult)
        )).thenReturn(new com.ragadmin.server.chat.dto.ChatResponse(
                1002L,
                "可以重点关注智能体应用落地。",
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(96, 26)
        ));

        appChatService.chat(502L, request, user(6002L));

        ArgumentCaptor<List<ChatModelClient.ChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(conversationChatClient).chat(eq("BAILIAN"), eq("qwen-max"), any(), promptCaptor.capture(), any());
        List<ChatModelClient.ChatMessage> promptMessages = promptCaptor.getValue();
        assertEquals(2, promptMessages.size());
        assertTrue(promptMessages.get(1).content().contains("联网搜索摘要"));
        assertTrue(promptMessages.get(1).content().contains("行业快讯"));
        assertTrue(promptMessages.get(1).content().contains("https://example.com/news"));
    }

    private AuthenticatedUser user(Long userId) {
        return new AuthenticatedUser()
                .setUserId(userId)
                .setUsername("tester")
                .setSessionId("session-" + userId);
    }
}
