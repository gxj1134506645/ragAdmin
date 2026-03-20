package com.ragadmin.server.app.service;

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
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.ConversationIdCodec;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryManager;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryRefreshDispatcher;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
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

    private AuthenticatedUser user(Long userId) {
        return new AuthenticatedUser()
                .setUserId(userId)
                .setUsername("tester")
                .setSessionId("session-" + userId);
    }
}
