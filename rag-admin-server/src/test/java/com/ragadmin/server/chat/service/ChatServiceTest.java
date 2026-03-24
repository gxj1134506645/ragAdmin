package com.ragadmin.server.chat.service;

import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.dto.ChatRequest;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatStreamEventResponse;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
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
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadata;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadataGenerationService;
import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatExecutionPlan;
import com.ragadmin.server.infra.ai.chat.ChatExecutionPlanningRequest;
import com.ragadmin.server.infra.ai.chat.ChatExecutionPlanningService;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.ConversationIdCodec;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryRefreshDispatcher;
import com.ragadmin.server.infra.ai.chat.PromptTemplateService;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.service.RetrievalService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.Generation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Flux;

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
    private ChatExecutionPlanningService chatExecutionPlanningService;

    @Mock
    private ChatAnswerMetadataGenerationService chatAnswerMetadataGenerationService;

    @Mock
    private ConversationMemoryRefreshDispatcher conversationMemoryRefreshDispatcher;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Mock
    private ChatExchangePersistenceService chatExchangePersistenceService;

    @Spy
    private PromptTemplateService promptTemplateService = new PromptTemplateService();

    @Spy
    private ConversationIdCodec conversationIdCodec = new ConversationIdCodec();

    @InjectMocks
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        configurePromptTemplates();
        lenient().doAnswer(invocation -> {
            ChatExecutionPlanningRequest planningRequest = invocation.getArgument(0);
            return new ChatExecutionPlan(
                    planningRequest.retrievalAvailable() ? "KNOWLEDGE_BASE_QA" : "GENERAL_QA",
                    planningRequest.retrievalAvailable(),
                    planningRequest.retrievalAvailable() ? planningRequest.question() : "",
                    false,
                    "",
                    "测试默认规则规划",
                    "RULE_BASED"
            );
        }).when(chatExecutionPlanningService).plan(any());
        lenient().when(chatAnswerMetadataGenerationService.generate(any()))
                .thenReturn(new ChatAnswerMetadata("LOW", false, false));
    }

    private void configurePromptTemplates() {
        setTemplate("knowledgeBaseSystemPromptTemplate", "prompts/ai/chat/admin-knowledge-system.st");
        setTemplate("generalSystemPromptTemplate", "prompts/ai/chat/admin-general-system.st");
        setTemplate("knowledgeBaseUserContextPromptTemplate", "prompts/ai/chat/admin-knowledge-user-context.st");
        setTemplate("knowledgeBaseUserNoContextPromptTemplate", "prompts/ai/chat/admin-knowledge-user-no-context.st");
    }

    private void setTemplate(String fieldName, String classpathLocation) {
        ReflectionTestUtils.setField(chatService, fieldName, new ClassPathResource(classpathLocation));
    }

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
    void shouldIncludePersistedMetadataWhenListingMessages() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(26L);
        session.setUserId(100L);

        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(601L);
        message.setSessionId(26L);
        message.setQuestionText("制度里是否要求提交周报？");
        message.setAnswerText("制度要求按时提交周报。");
        message.setAnswerConfidence("HIGH");
        message.setHasKnowledgeBaseEvidence(Boolean.TRUE);
        message.setNeedFollowUp(Boolean.FALSE);

        when(chatSessionMapper.selectById(26L)).thenReturn(session);
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(message));
        when(chatAnswerReferenceMapper.selectList(any())).thenReturn(List.of());
        when(chatFeedbackMapper.selectList(any())).thenReturn(List.of());

        List<ChatMessageResponse> messages = chatService.listMessages(26L, user(100L));

        assertEquals(1, messages.size());
        assertNotNull(messages.getFirst().metadata());
        assertEquals("HIGH", messages.getFirst().metadata().confidence());
        assertEquals(true, messages.getFirst().metadata().hasKnowledgeBaseEvidence());
        assertEquals(false, messages.getFirst().metadata().needFollowUp());
        verify(chunkMapper, never()).selectBatchIds(any());
        verify(documentMapper, never()).selectBatchIds(any());
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
        when(modelService.resolveChatModelDescriptor(null)).thenReturn(modelDescriptor);
        when(conversationChatClient.chat(eq("OLLAMA"), eq("qwen2.5:7b"), any(), any(), any()))
                .thenReturn(new ChatCompletionResult("制度要求按时提交周报。", 120, 30));
        ChatAnswerMetadata metadata = new ChatAnswerMetadata("HIGH", true, false);
        when(chatAnswerMetadataGenerationService.generate(any())).thenReturn(metadata);
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(100L),
                eq("请总结制度要点"),
                eq("制度要求按时提交周报。"),
                eq(801L),
                eq(120),
                eq(30),
                anyInt(),
                eq(metadata),
                eq(retrievalResult),
                any()
        )).thenReturn(new ChatResponse(
                901L,
                "制度要求按时提交周报。",
                "text/markdown",
                List.of(new com.ragadmin.server.chat.dto.ChatReferenceResponse(
                        401L,
                        701L,
                        "员工制度.md",
                        601L,
                        1,
                        0.91D,
                        "制度要求员工按时提交周报。"
                )),
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(120, 30),
                new com.ragadmin.server.chat.dto.ChatAnswerMetadataResponse("HIGH", true, false)
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
                eq(metadata),
                eq(retrievalResult),
                any()
        );

        assertEquals("制度要求按时提交周报。", response.answer());
        assertEquals(1, response.references().size());
        assertEquals(120, response.usage().promptTokens());
        assertEquals(30, response.usage().completionTokens());
        assertEquals("HIGH", response.metadata().confidence());
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
        when(modelService.resolveChatModelDescriptor(null)).thenReturn(modelDescriptor);
        when(conversationChatClient.chat(eq("OLLAMA"), eq("qwen2.5:7b"), any(), any(), any()))
                .thenReturn(new ChatCompletionResult("当前无法从知识库确认答案。", 64, 18));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(100L),
                eq("没有命中时应该怎么回答"),
                eq("当前无法从知识库确认答案。"),
                eq(802L),
                eq(64),
                eq(18),
                anyInt(),
                any(),
                eq(retrievalResult),
                any()
        )).thenReturn(new ChatResponse(
                902L,
                "当前无法从知识库确认答案。",
                "text/markdown",
                List.of(),
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(64, 18),
                null
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
                any(),
                eq(retrievalResult),
                any()
        );
        assertEquals("当前无法从知识库确认答案。", response.answer());
        assertEquals(0, response.references().size());
        assertEquals(64, response.usage().promptTokens());
        assertEquals(18, response.usage().completionTokens());
    }

    @Test
    void shouldUseStructuredPlanRetrievalQueryWhenAvailable() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(33L);
        session.setKbId(403L);
        session.setUserId(100L);

        ChatRequest request = new ChatRequest();
        request.setKbId(403L);
        request.setQuestion("请总结制度里和周报有关的要求");

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(403L);

        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(List.of(), "片段1:\n周报需按时提交。");
        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                803L,
                "qwen2.5:7b",
                "OLLAMA",
                "Ollama"
        );

        when(chatSessionMapper.selectById(33L)).thenReturn(session);
        when(knowledgeBaseService.requireById(403L)).thenReturn(knowledgeBase);
        when(modelService.resolveChatModelDescriptor(null)).thenReturn(modelDescriptor);
        doReturn(new ChatExecutionPlan(
                "KNOWLEDGE_BASE_QA",
                true,
                "制度 周报 提交 要求",
                false,
                "",
                "问题明确指向知识库制度要求",
                "MODEL"
        )).when(chatExecutionPlanningService).plan(any());
        when(retrievalService.retrieve(knowledgeBase, "制度 周报 提交 要求")).thenReturn(retrievalResult);
        when(conversationChatClient.chat(eq("OLLAMA"), eq("qwen2.5:7b"), any(), any(), any()))
                .thenReturn(new ChatCompletionResult("制度要求按时提交周报。", 100, 20));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(100L),
                eq("请总结制度里和周报有关的要求"),
                eq("制度要求按时提交周报。"),
                eq(803L),
                eq(100),
                eq(20),
                anyInt(),
                any(),
                eq(retrievalResult),
                any()
        )).thenReturn(new ChatResponse(
                903L,
                "制度要求按时提交周报。",
                "text/markdown",
                List.of(),
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(100, 20),
                null
        ));

        chatService.chat(33L, request, user(100L));

        verify(retrievalService).retrieve(knowledgeBase, "制度 周报 提交 要求");
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

    @Test
    void shouldReturnErrorEventWhenStreamPreparationFailed() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("会话还在吗？");
        request.setKbId(1001L);

        List<ChatStreamEventResponse> events = chatService.streamChat(999L, request, user(100L))
                .collectList()
                .block();

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("ERROR", events.getFirst().eventType());
        assertEquals("会话不存在", events.getFirst().errorMessage());
    }

    @Test
    void shouldIncludeMetadataInStreamCompleteEvent() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(34L);
        session.setKbId(404L);
        session.setUserId(100L);

        ChatRequest request = new ChatRequest();
        request.setKbId(404L);
        request.setQuestion("请总结制度里和周报有关的要求");

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(404L);

        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(List.of(), "片段1:\n周报需按时提交。");
        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                804L,
                "qwen2.5:7b",
                "OLLAMA",
                "Ollama"
        );
        ChatAnswerMetadata metadata = new ChatAnswerMetadata("MEDIUM", true, false);

        when(chatSessionMapper.selectById(34L)).thenReturn(session);
        when(knowledgeBaseService.requireById(404L)).thenReturn(knowledgeBase);
        when(modelService.resolveChatModelDescriptor(null)).thenReturn(modelDescriptor);
        when(retrievalService.retrieve(knowledgeBase, "请总结制度里和周报有关的要求")).thenReturn(retrievalResult);
        when(conversationChatClient.stream(eq("OLLAMA"), eq("qwen2.5:7b"), any(), any(), any()))
                .thenReturn(Flux.just(chatChunk("制度要求", 100, 20), chatChunk("按时提交周报。", 100, 20)));
        when(chatAnswerMetadataGenerationService.generate(any())).thenReturn(metadata);
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(100L),
                eq("请总结制度里和周报有关的要求"),
                eq("制度要求按时提交周报。"),
                eq(804L),
                eq(100),
                eq(20),
                anyInt(),
                eq(metadata),
                eq(retrievalResult),
                any()
        )).thenReturn(new ChatResponse(
                904L,
                "制度要求按时提交周报。",
                "text/markdown",
                List.of(),
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(100, 20),
                new com.ragadmin.server.chat.dto.ChatAnswerMetadataResponse("MEDIUM", true, false)
        ));

        List<ChatStreamEventResponse> events = chatService.streamChat(34L, request, user(100L)).collectList().block();

        assertNotNull(events);
        assertEquals(3, events.size());
        assertEquals("DELTA", events.get(0).eventType());
        assertEquals("制度要求", events.get(0).delta());
        assertEquals("COMPLETE", events.get(2).eventType());
        assertEquals("MEDIUM", events.get(2).metadata().confidence());
        assertEquals(true, events.get(2).metadata().hasKnowledgeBaseEvidence());
    }

    @Test
    void shouldIgnoreEmptyStreamChunkAndStillCompleteInAdminChat() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(35L);
        session.setUserId(100L);
        session.setSceneType(ChatSceneTypes.GENERAL);

        ChatRequest request = new ChatRequest();
        request.setQuestion("制度重点是什么？");

        ModelService.ChatModelDescriptor modelDescriptor = new ModelService.ChatModelDescriptor(
                501L,
                "qwen-max",
                "BAILIAN",
                "百炼"
        );
        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(List.of(), "");

        when(chatSessionMapper.selectById(35L)).thenReturn(session);
        when(modelService.resolveChatModelDescriptor(null)).thenReturn(modelDescriptor);
        when(conversationChatClient.stream(eq("BAILIAN"), eq("qwen-max"), any(), any(), any()))
                .thenReturn(Flux.just(
                        chatChunk("先看制度要求。", 12, 4),
                        chatChunk("", 12, 16)
                ));
        when(chatExchangePersistenceService.persistExchange(
                eq(session),
                eq(100L),
                eq("制度重点是什么？"),
                eq("先看制度要求。"),
                eq(501L),
                eq(12),
                eq(16),
                anyInt(),
                any(),
                eq(retrievalResult),
                any()
        )).thenReturn(new ChatResponse(
                2002L,
                "先看制度要求。",
                "text/markdown",
                List.of(),
                List.of(),
                new com.ragadmin.server.chat.dto.ChatUsageResponse(12, 16),
                null
        ));

        List<ChatStreamEventResponse> events = chatService.streamChat(35L, request, user(100L)).collectList().block();

        assertNotNull(events);
        assertEquals(2, events.size());
        assertEquals("DELTA", events.get(0).eventType());
        assertEquals("先看制度要求。", events.get(0).delta());
        assertEquals("COMPLETE", events.get(1).eventType());
        assertEquals(2002L, events.get(1).messageId());
    }

    private org.springframework.ai.chat.model.ChatResponse chatChunk(String text, int promptTokens, int completionTokens) {
        return org.springframework.ai.chat.model.ChatResponse.builder()
                .metadata(ChatResponseMetadata.builder()
                        .usage(new Usage() {
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
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }

    private AuthenticatedUser user(Long userId) {
        return new AuthenticatedUser().setUserId(userId).setUsername("tester").setSessionId("session-" + userId);
    }
}
