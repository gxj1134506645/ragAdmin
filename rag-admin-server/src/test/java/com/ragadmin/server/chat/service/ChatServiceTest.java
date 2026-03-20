package com.ragadmin.server.chat.service;

import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.chat.dto.ChatRequest;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatFeedbackEntity;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatFeedbackMapper;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.ConversationIdCodec;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

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
    private ChatService chatService;

    @Test
    void shouldRejectListingMessagesForOtherUsersSession() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(11L);
        session.setUserId(200L);

        when(chatSessionMapper.selectById(11L)).thenReturn(session);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chatService.listMessages(11L, user(100L))
        );

        assertEquals("CHAT_SESSION_NOT_FOUND", exception.getCode());
        verify(chatMessageMapper, never()).selectList(any());
    }

    @Test
    void shouldRejectChatWhenKnowledgeBaseDoesNotMatchSession() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(21L);
        session.setKbId(300L);
        session.setUserId(100L);

        ChatRequest request = new ChatRequest();
        request.setKbId(301L);
        request.setQuestion("知识库里有什么内容？");

        when(chatSessionMapper.selectById(21L)).thenReturn(session);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> chatService.chat(21L, request, user(100L))
        );

        assertEquals("CHAT_KB_MISMATCH", exception.getCode());
        verify(retrievalService, never()).retrieve(any(), any());
    }

    @Test
    void shouldPersistMessageAndReferencesWhenChatSucceeded() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(31L);
        session.setKbId(401L);
        session.setUserId(100L);

        ChatRequest request = new ChatRequest();
        request.setKbId(401L);
        request.setQuestion("请总结制度要点");

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(401L);
        knowledgeBase.setChatModelId(501L);

        ChunkEntity chunk = new ChunkEntity();
        chunk.setId(601L);
        chunk.setDocumentId(701L);
        chunk.setChunkNo(1);
        chunk.setChunkText("制度要求员工按时提交周报。");

        RetrievalService.RetrievedChunk retrievedChunk = new RetrievalService.RetrievedChunk(chunk, 0.91D);
        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(
                List.of(retrievedChunk),
                "片段1:\n制度要求员工按时提交周报。"
        );

        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                801L,
                "qwen2.5:7b",
                "OLLAMA",
                "Ollama"
        );

        when(chatSessionMapper.selectById(31L)).thenReturn(session);
        when(knowledgeBaseService.requireById(401L)).thenReturn(knowledgeBase);
        when(retrievalService.retrieve(knowledgeBase, "请总结制度要点")).thenReturn(retrievalResult);
        when(modelService.resolveChatModelDescriptor(501L)).thenReturn(modelDescriptor);
        when(conversationChatClient.chat(eq("OLLAMA"), eq("qwen2.5:7b"), any(), any(), any()))
                .thenReturn(new ChatModelClient.ChatCompletionResult("制度要求按时提交周报。", 120, 30));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(100L),
                eq("请总结制度要点"),
                eq("制度要求按时提交周报。"),
                eq(801L),
                eq(120),
                eq(30),
                anyInt(),
                eq(retrievalResult)
        )).thenReturn(new ChatResponse(
                901L,
                "制度要求按时提交周报。",
                List.of(new com.ragadmin.server.chat.dto.ChatReferenceResponse(
                        401L,
                        701L,
                        "员工制度.md",
                        601L,
                        1,
                        0.91D,
                        "制度要求员工按时提交周报。"
                )),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(120, 30)
        ));

        ChatResponse response = chatService.chat(31L, request, user(100L));

        verify(conversationChatClient).chat(eq("OLLAMA"), eq("qwen2.5:7b"), any(), any(), any());
        verify(chatExchangePersistenceService).persistExchange(
                eq(session),
                eq(100L),
                eq("请总结制度要点"),
                eq("制度要求按时提交周报。"),
                eq(801L),
                eq(120),
                eq(30),
                anyInt(),
                eq(retrievalResult)
        );

        assertEquals("制度要求按时提交周报。", response.answer());
        assertEquals(1, response.references().size());
        assertEquals(120, response.usage().promptTokens());
        assertEquals(30, response.usage().completionTokens());
        verify(conversationMemoryRefreshDispatcher).dispatchRefresh("chat-terminal-admin-scene-knowledge_base-user-100-session-31");
    }

    @Test
    void shouldAllowChatWithoutRetrievedReferences() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(32L);
        session.setKbId(402L);
        session.setUserId(100L);

        ChatRequest request = new ChatRequest();
        request.setKbId(402L);
        request.setQuestion("没有命中时应该怎么回答");

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(402L);
        knowledgeBase.setChatModelId(502L);

        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(
                List.of(),
                ""
        );

        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                802L,
                "qwen2.5:7b",
                "OLLAMA",
                "Ollama"
        );

        when(chatSessionMapper.selectById(32L)).thenReturn(session);
        when(knowledgeBaseService.requireById(402L)).thenReturn(knowledgeBase);
        when(retrievalService.retrieve(knowledgeBase, "没有命中时应该怎么回答")).thenReturn(retrievalResult);
        when(modelService.resolveChatModelDescriptor(502L)).thenReturn(modelDescriptor);
        when(conversationChatClient.chat(eq("OLLAMA"), eq("qwen2.5:7b"), any(), any(), any()))
                .thenReturn(new ChatModelClient.ChatCompletionResult("当前无法从知识库确认答案。", 64, 18));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(100L),
                eq("没有命中时应该怎么回答"),
                eq("当前无法从知识库确认答案。"),
                eq(802L),
                eq(64),
                eq(18),
                anyInt(),
                eq(retrievalResult)
        )).thenReturn(new ChatResponse(
                902L,
                "当前无法从知识库确认答案。",
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(64, 18)
        ));

        ChatResponse response = chatService.chat(32L, request, user(100L));

        verify(documentMapper, never()).selectBatchIds(any());
        verify(chatAnswerReferenceMapper, never()).insert(isA(ChatAnswerReferenceEntity.class));
        verify(chatExchangePersistenceService).persistExchange(
                eq(session),
                eq(100L),
                eq("没有命中时应该怎么回答"),
                eq("当前无法从知识库确认答案。"),
                eq(802L),
                eq(64),
                eq(18),
                anyInt(),
                eq(retrievalResult)
        );
        assertEquals("当前无法从知识库确认答案。", response.answer());
        assertEquals(0, response.references().size());
        assertEquals(64, response.usage().promptTokens());
        assertEquals(18, response.usage().completionTokens());
    }

    @Test
    void shouldInsertFeedbackWhenFeedbackDoesNotExist() {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(41L);
        message.setSessionId(51L);

        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(51L);
        session.setUserId(100L);

        when(chatMessageMapper.selectById(41L)).thenReturn(message);
        when(chatSessionMapper.selectById(51L)).thenReturn(session);
        when(chatFeedbackMapper.selectOne(any())).thenReturn(null);

        chatService.submitFeedback(41L, "LIKE", "回答准确", user(100L));

        ArgumentCaptor<ChatFeedbackEntity> captor = ArgumentCaptor.forClass(ChatFeedbackEntity.class);
        verify(chatFeedbackMapper).insert(captor.capture());
        ChatFeedbackEntity inserted = captor.getValue();
        assertEquals(41L, inserted.getMessageId());
        assertEquals(100L, inserted.getUserId());
        assertEquals("LIKE", inserted.getFeedbackType());
        assertEquals("回答准确", inserted.getCommentText());
    }

    private AuthenticatedUser user(Long userId) {
        return new AuthenticatedUser().setUserId(userId).setUsername("tester").setSessionId("session-" + userId);
    }
}
