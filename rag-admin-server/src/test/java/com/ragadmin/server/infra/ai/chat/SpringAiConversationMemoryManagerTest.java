package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.ChatTerminalTypes;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.entity.ChatSessionMemorySummaryEntity;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMemorySummaryMapper;
import com.ragadmin.server.model.service.ModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiConversationMemoryManagerTest {

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatSessionMemorySummaryMapper chatSessionMemorySummaryMapper;

    @Mock
    private RedisShortTermChatMemoryStore redisShortTermChatMemoryStore;

    @Mock
    private ConversationSummaryGenerator conversationSummaryGenerator;

    @Mock
    private ConversationSummaryLockManager conversationSummaryLockManager;

    @Mock
    private ModelService modelService;

    @Spy
    private ConversationIdCodec conversationIdCodec = new ConversationIdCodec();

    @Spy
    private ChatMemoryProperties chatMemoryProperties = new ChatMemoryProperties();

    @InjectMocks
    private SpringAiConversationMemoryManager conversationMemoryManager;

    @BeforeEach
    void setUp() {
        chatMemoryProperties.setShortTermRounds(2);
        chatMemoryProperties.setSummaryMaxLength(120);
    }

    @Test
    void shouldSkipRefreshWhenSummaryLockNotAcquired() {
        String conversationId = buildConversationId(101L, 1001L, 501L);
        when(conversationSummaryLockManager.tryLock(conversationId)).thenReturn(Optional.empty());

        conversationMemoryManager.refresh(conversationId);

        verify(chatMessageMapper, never()).selectList(any());
        verify(conversationSummaryLockManager, never()).unlock(any());
    }

    @Test
    void shouldRefreshSummaryAndShortTermMemoryWhenConversationExceededWindow() {
        String conversationId = buildConversationId(102L, 1002L, 502L);
        ConversationSummaryLockManager.LockToken lockToken =
                new ConversationSummaryLockManager.LockToken("lock-key", "owner-token");
        RedisShortTermChatMemoryStore.StoredConversationMemory storedMemory =
                new RedisShortTermChatMemoryStore.StoredConversationMemory("摘要内容", List.of(), 12, "2026-03-20T12:00:00Z");

        when(conversationSummaryLockManager.tryLock(conversationId)).thenReturn(Optional.of(lockToken));
        when(conversationSummaryLockManager.unlock(lockToken)).thenReturn(true);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(
                exchange(1L, 502L, "第一问", "第一答"),
                exchange(2L, 502L, "第二问", "第二答"),
                exchange(3L, 502L, "第三问", "第三答")
        ));
        when(chatSessionMapper.selectById(502L)).thenReturn(session(102L, 1002L, 502L));
        when(modelService.resolveChatModelDescriptor(102L)).thenReturn(
                new ModelService.ChatModelDescriptor(102L, "qwen-plus", "BAILIAN", "百炼")
        );
        when(conversationSummaryGenerator.generate(any())).thenReturn(
                new ConversationSummaryResult("摘要内容", ConversationSummarySource.MODEL, 2)
        );
        when(redisShortTermChatMemoryStore.build(eq("摘要内容"), anyList())).thenReturn(storedMemory);

        conversationMemoryManager.refresh(conversationId);

        ArgumentCaptor<ConversationSummaryRequest> requestCaptor = ArgumentCaptor.forClass(ConversationSummaryRequest.class);
        verify(conversationSummaryGenerator).generate(requestCaptor.capture());
        ConversationSummaryRequest request = requestCaptor.getValue();
        assertEquals("BAILIAN", request.providerCode());
        assertEquals("qwen-plus", request.modelCode());
        assertEquals(2, request.messages().size());
        assertEquals("第一问", request.messages().getFirst().content());

        ArgumentCaptor<ChatSessionMemorySummaryEntity> summaryCaptor = ArgumentCaptor.forClass(ChatSessionMemorySummaryEntity.class);
        verify(chatSessionMemorySummaryMapper).insert((ChatSessionMemorySummaryEntity) summaryCaptor.capture());
        ChatSessionMemorySummaryEntity summaryEntity = summaryCaptor.getValue();
        assertEquals(502L, summaryEntity.getSessionId());
        assertEquals(conversationId, summaryEntity.getConversationId());
        assertEquals("摘要内容", summaryEntity.getSummaryText());
        assertEquals(1, summaryEntity.getCompressedMessageCount());
        assertEquals(1L, summaryEntity.getCompressedUntilMessageId());
        assertEquals(1L, summaryEntity.getLastSourceMessageId());
        assertEquals(1, summaryEntity.getSummaryVersion());

        ArgumentCaptor<List<Message>> recentMessagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisShortTermChatMemoryStore).build(eq("摘要内容"), recentMessagesCaptor.capture());
        List<Message> recentMessages = recentMessagesCaptor.getValue();
        assertEquals(4, recentMessages.size());
        assertEquals("第二问", recentMessages.get(0).getText());
        assertEquals("第三答", recentMessages.get(3).getText());
        verify(redisShortTermChatMemoryStore).save(eq(conversationId), same(storedMemory));
        verify(conversationSummaryLockManager).unlock(lockToken);
    }

    @Test
    void shouldSwallowRefreshExceptionAndReleaseLock() {
        String conversationId = buildConversationId(103L, 1003L, 503L);
        ConversationSummaryLockManager.LockToken lockToken =
                new ConversationSummaryLockManager.LockToken("lock-key", "owner-token");

        when(conversationSummaryLockManager.tryLock(conversationId)).thenReturn(Optional.of(lockToken));
        when(conversationSummaryLockManager.unlock(lockToken)).thenReturn(true);
        when(chatMessageMapper.selectList(any())).thenThrow(new RuntimeException("db error"));

        assertDoesNotThrow(() -> conversationMemoryManager.refresh(conversationId));

        verify(conversationSummaryLockManager).unlock(lockToken);
        verify(chatSessionMemorySummaryMapper, never()).insert(any(ChatSessionMemorySummaryEntity.class));
        verify(redisShortTermChatMemoryStore, never()).save(any(), any());
    }

    private String buildConversationId(Long modelId, Long userId, Long sessionId) {
        return conversationIdCodec.encode(session(modelId, userId, sessionId));
    }

    private ChatSessionEntity session(Long modelId, Long userId, Long sessionId) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setModelId(modelId);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSceneType(ChatSceneTypes.GENERAL);
        return session;
    }

    private ChatMessageEntity exchange(Long id, Long sessionId, String question, String answer) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(id);
        entity.setSessionId(sessionId);
        entity.setQuestionText(question);
        entity.setAnswerText(answer);
        return entity;
    }
}
