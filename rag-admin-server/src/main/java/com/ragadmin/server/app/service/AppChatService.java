package com.ragadmin.server.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.app.dto.AppChatRequest;
import com.ragadmin.server.app.dto.AppRegenerateChatMessageRequest;
import com.ragadmin.server.app.dto.AppChatSessionResponse;
import com.ragadmin.server.app.dto.AppCreateChatSessionRequest;
import com.ragadmin.server.app.dto.AppUpdateChatSessionRequest;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.chat.ChatContentTypes;
import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.ChatTerminalTypes;
import com.ragadmin.server.chat.dto.ChatFeedbackRequest;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatStreamEventResponse;
import com.ragadmin.server.chat.dto.WebSearchSourceResponse;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatFeedbackEntity;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.entity.ChatSessionKnowledgeBaseRelEntity;
import com.ragadmin.server.chat.entity.ChatWebSearchSourceEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatFeedbackMapper;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionKnowledgeBaseRelMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.chat.mapper.ChatWebSearchSourceMapper;
import com.ragadmin.server.chat.service.ChatExchangePersistenceService;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadata;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadataGenerationRequest;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadataGenerationService;
import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatExecutionPlanningRequest;
import com.ragadmin.server.infra.ai.chat.ChatExecutionPlanningService;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.ConversationIdCodec;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryManager;
import com.ragadmin.server.infra.ai.chat.ConversationMemoryRefreshDispatcher;
import com.ragadmin.server.infra.ai.chat.PromptTemplateService;
import com.ragadmin.server.infra.ai.AiProviderExceptionSupport;
import com.ragadmin.server.infra.search.NoopWebSearchProvider;
import com.ragadmin.server.infra.search.WebSearchProperties;
import com.ragadmin.server.infra.search.WebSearchProvider;
import com.ragadmin.server.infra.search.WebSearchSnippet;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.service.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class AppChatService {

    private static final Logger log = LoggerFactory.getLogger(AppChatService.class);
    private static final ZoneId APP_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC+8'");

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionKnowledgeBaseRelMapper chatSessionKnowledgeBaseRelMapper;

    @Autowired
    private ChatAnswerReferenceMapper chatAnswerReferenceMapper;

    @Autowired
    private ChatFeedbackMapper chatFeedbackMapper;

    @Autowired
    private ChatWebSearchSourceMapper chatWebSearchSourceMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private ConversationChatClient conversationChatClient;

    @Autowired
    private ChatExecutionPlanningService chatExecutionPlanningService;

    @Autowired
    private ChatAnswerMetadataGenerationService chatAnswerMetadataGenerationService;

    @Autowired
    private ConversationMemoryManager conversationMemoryManager;

    @Autowired
    private ConversationMemoryRefreshDispatcher conversationMemoryRefreshDispatcher;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private ChunkMapper chunkMapper;

    @Autowired
    private ChatExchangePersistenceService chatExchangePersistenceService;

    @Autowired
    private ConversationIdCodec conversationIdCodec;

    @Autowired
    private PromptTemplateService promptTemplateService;

    /**
     * 联网搜索当前仍属于可选能力，没有真实 provider 时自动回退为空实现，避免应用启动失败。
     */
    @Autowired(required = false)
    private WebSearchProvider webSearchProvider = new NoopWebSearchProvider();

    @Autowired(required = false)
    private WebSearchProperties webSearchProperties = new WebSearchProperties();

    /**
     * 问答链路中的相对时间解释统一按北京时间处理，避免“今天/明天”在模型侧被误判成语义不明确。
     */
    @Autowired(required = false)
    private Clock clock = Clock.system(APP_ZONE_ID);

    @Value("classpath:prompts/ai/chat/app-knowledge-system.st")
    private Resource knowledgeBaseSystemPromptTemplate;

    @Value("classpath:prompts/ai/chat/app-general-system.st")
    private Resource generalSystemPromptTemplate;

    @Value("classpath:prompts/ai/chat/app-knowledge-user-context-with-web.st")
    private Resource knowledgeBaseUserContextWithWebPromptTemplate;

    @Value("classpath:prompts/ai/chat/app-knowledge-user-context-only.st")
    private Resource knowledgeBaseUserContextOnlyPromptTemplate;

    @Value("classpath:prompts/ai/chat/app-knowledge-user-web-only.st")
    private Resource knowledgeBaseUserWebOnlyPromptTemplate;

    @Value("classpath:prompts/ai/chat/app-knowledge-user-no-context.st")
    private Resource knowledgeBaseUserNoContextPromptTemplate;

    @Value("classpath:prompts/ai/chat/app-general-user-with-web.st")
    private Resource generalUserWithWebPromptTemplate;

    @Value("classpath:prompts/ai/chat/app-general-user-plain.st")
    private Resource generalUserPlainPromptTemplate;

    @Transactional
    public AppChatSessionResponse createSession(AppCreateChatSessionRequest request, AuthenticatedUser user) {
        String sceneType = normalizeAppSceneType(request.getSceneType(), request.getKbId());
        List<Long> selectedKbIds = normalizeSelectedKnowledgeBaseIds(sceneType, request.getKbId(), request.getSelectedKbIds());

        ChatSessionEntity session = new ChatSessionEntity();
        session.setKbId(ChatSceneTypes.KNOWLEDGE_BASE.equals(sceneType) ? requireKnowledgeBaseId(request.getKbId()) : null);
        session.setUserId(user.getUserId());
        session.setSceneType(sceneType);
        session.setTerminalType(ChatTerminalTypes.APP);
        session.setSessionName(request.getSessionName());
        session.setModelId(request.getChatModelId());
        session.setWebSearchEnabled(Boolean.TRUE.equals(request.getWebSearchEnabled()));
        session.setStatus("ENABLED");
        chatSessionMapper.insert(session);
        replaceSessionKnowledgeBaseRelations(session.getId(), selectedKbIds);
        return toSessionResponse(session, selectedKbIds);
    }

    public PageResponse<AppChatSessionResponse> listSessions(Long kbId, String sceneType, AuthenticatedUser user, long pageNo, long pageSize) {
        String normalizedSceneType = normalizeOptionalSceneType(sceneType);
        Page<ChatSessionEntity> page = chatSessionMapper.selectPage(Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getUserId, user.getUserId())
                        .eq(ChatSessionEntity::getTerminalType, ChatTerminalTypes.APP)
                        .eq(StringUtils.hasText(normalizedSceneType), ChatSessionEntity::getSceneType, normalizedSceneType)
                        .eq(kbId != null, ChatSessionEntity::getKbId, kbId)
                        .orderByDesc(ChatSessionEntity::getId));
        Map<Long, List<Long>> selectedKbIdMap = loadSelectedKnowledgeBaseIds(page.getRecords().stream()
                .map(ChatSessionEntity::getId)
                .toList());
        return new PageResponse<>(
                page.getRecords().stream()
                        .map(session -> toSessionResponse(
                                session,
                                selectedKbIdMap.getOrDefault(session.getId(), defaultSelectedKnowledgeBaseIds(session))
                        ))
                        .toList(),
                pageNo,
                pageSize,
                page.getTotal()
        );
    }

    public List<ChatMessageResponse> listMessages(Long sessionId, AuthenticatedUser user) {
        ChatSessionEntity session = requireAppSession(sessionId, user.getUserId());
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, session.getId())
                .orderByAsc(ChatMessageEntity::getId));
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> messageIds = messages.stream().map(ChatMessageEntity::getId).toList();
        Map<Long, List<ChatAnswerReferenceEntity>> refsByMessageId = chatAnswerReferenceMapper.selectList(new LambdaQueryWrapper<ChatAnswerReferenceEntity>()
                        .in(ChatAnswerReferenceEntity::getMessageId, messageIds))
                .stream()
                .collect(Collectors.groupingBy(ChatAnswerReferenceEntity::getMessageId));
        Map<Long, List<WebSearchSourceResponse>> webSearchSourcesByMessageId = chatWebSearchSourceMapper.selectList(
                        new LambdaQueryWrapper<ChatWebSearchSourceEntity>()
                                .in(ChatWebSearchSourceEntity::getMessageId, messageIds)
                                .orderByAsc(ChatWebSearchSourceEntity::getMessageId, ChatWebSearchSourceEntity::getRankNo))
                .stream()
                .collect(Collectors.groupingBy(
                        ChatWebSearchSourceEntity::getMessageId,
                        Collectors.mapping(this::toWebSearchSourceResponse, Collectors.toList())
                ));
        Map<Long, String> documentNameMap = resolveDocumentNames(refsByMessageId.values().stream()
                .flatMap(List::stream)
                .map(ChatAnswerReferenceEntity::getChunkId)
                .toList());
        List<Long> chunkIds = refsByMessageId.values().stream()
                .flatMap(List::stream)
                .map(ChatAnswerReferenceEntity::getChunkId)
                .distinct()
                .toList();
        // MyBatis Plus 在 PostgreSQL 上会把空集合拼成 IN ()，这里必须提前短路。
        Map<Long, ChunkEntity> chunkMap = chunkIds.isEmpty()
                ? Map.of()
                : chunkMapper.selectBatchIds(chunkIds)
                .stream()
                .collect(Collectors.toMap(ChunkEntity::getId, java.util.function.Function.identity()));
        Map<Long, ChatFeedbackEntity> feedbackByMessageId = chatFeedbackMapper.selectList(new LambdaQueryWrapper<ChatFeedbackEntity>()
                        .in(ChatFeedbackEntity::getMessageId, messageIds)
                        .eq(ChatFeedbackEntity::getUserId, user.getUserId()))
                .stream()
                .collect(Collectors.toMap(
                        ChatFeedbackEntity::getMessageId,
                        java.util.function.Function.identity(),
                        (left, right) -> right
                ));

        return messages.stream()
                .map(message -> {
                    ChatFeedbackEntity feedback = feedbackByMessageId.get(message.getId());
                    return new ChatMessageResponse(
                            message.getId(),
                            message.getQuestionText(),
                            message.getAnswerText(),
                            ChatContentTypes.MARKDOWN,
                            refsByMessageId.getOrDefault(message.getId(), List.of()).stream()
                                    .map(ref -> toReferenceResponse(ref, chunkMap.get(ref.getChunkId()), documentNameMap))
                                    .toList(),
                            webSearchSourcesByMessageId.getOrDefault(message.getId(), List.of()),
                            toMetadataResponse(message),
                            feedback == null ? null : feedback.getFeedbackType(),
                            feedback == null ? null : feedback.getCommentText()
                    );
                })
                .toList();
    }

    @Transactional
    public AppChatSessionResponse updateSession(Long sessionId, AppUpdateChatSessionRequest request, AuthenticatedUser user) {
        ChatSessionEntity session = requireAppSession(sessionId, user.getUserId());
        boolean changed = false;

        String normalizedSessionName = request.getSessionName().trim();
        if (!Objects.equals(normalizedSessionName, session.getSessionName())) {
            session.setSessionName(normalizedSessionName);
            changed = true;
        }

        if (request.getChatModelId() != null) {
            // 前台会话层面显式切换模型时，先校验模型是否存在且具备聊天能力。
            modelService.resolveChatModelDescriptor(request.getChatModelId());
        }
        if (!Objects.equals(request.getChatModelId(), session.getModelId())) {
            session.setModelId(request.getChatModelId());
            changed = true;
        }

        boolean normalizedWebSearchEnabled = Boolean.TRUE.equals(request.getWebSearchEnabled());
        if (!Objects.equals(normalizedWebSearchEnabled, Boolean.TRUE.equals(session.getWebSearchEnabled()))) {
            session.setWebSearchEnabled(normalizedWebSearchEnabled);
            changed = true;
        }

        if (changed) {
            chatSessionMapper.updateById(session);
        }

        return toSessionResponse(
                session,
                loadSelectedKnowledgeBaseIds(session.getId()).getOrDefault(session.getId(), defaultSelectedKnowledgeBaseIds(session))
        );
    }

    @Transactional
    public void deleteSession(Long sessionId, AuthenticatedUser user) {
        ChatSessionEntity session = requireAppSession(sessionId, user.getUserId());
        List<Long> messageIds = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, session.getId()))
                .stream()
                .map(ChatMessageEntity::getId)
                .toList();

        if (!messageIds.isEmpty()) {
            chatAnswerReferenceMapper.delete(new LambdaQueryWrapper<ChatAnswerReferenceEntity>()
                    .in(ChatAnswerReferenceEntity::getMessageId, messageIds));
            chatWebSearchSourceMapper.delete(new LambdaQueryWrapper<ChatWebSearchSourceEntity>()
                    .in(ChatWebSearchSourceEntity::getMessageId, messageIds));
            chatFeedbackMapper.delete(new LambdaQueryWrapper<ChatFeedbackEntity>()
                    .in(ChatFeedbackEntity::getMessageId, messageIds));
        }

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, session.getId()));
        chatSessionKnowledgeBaseRelMapper.delete(new LambdaQueryWrapper<ChatSessionKnowledgeBaseRelEntity>()
                .eq(ChatSessionKnowledgeBaseRelEntity::getSessionId, session.getId()));
        conversationMemoryManager.clear(conversationIdCodec.encode(session));
        chatSessionMapper.deleteById(session.getId());
    }

    @Transactional
    public AppChatSessionResponse updateSessionKnowledgeBases(Long sessionId, List<Long> selectedKbIds, AuthenticatedUser user) {
        ChatSessionEntity session = requireAppSession(sessionId, user.getUserId());
        List<Long> normalizedSelectedKbIds = normalizeSelectedKnowledgeBaseIds(
                normalizeSceneType(session.getSceneType()),
                session.getKbId(),
                selectedKbIds
        );
        replaceSessionKnowledgeBaseRelations(session.getId(), normalizedSelectedKbIds);
        return toSessionResponse(session, normalizedSelectedKbIds);
    }

    @Transactional
    public ChatResponse chat(Long sessionId, AppChatRequest request, AuthenticatedUser user) {
        PreparedChatExecution execution = prepareChatExecution(sessionId, request, user);
        Instant start = Instant.now();
        ChatCompletionResult completion = conversationChatClient.chat(
                execution.chatModel().providerCode(),
                execution.chatModel().modelCode(),
                execution.conversationId(),
                execution.promptMessages(),
                execution.historyMessages()
        );
        int latencyMs = (int) Duration.between(start, Instant.now()).toMillis();
        ChatAnswerMetadata answerMetadata = generateAnswerMetadata(
                execution,
                request.getQuestion(),
                completion.content()
        );

        ChatResponse response = chatExchangePersistenceService.persistExchange(
                execution.session(),
                user.getUserId(),
                request.getQuestion(),
                completion.content(),
                execution.chatModel().modelId(),
                completion.promptTokens(),
                completion.completionTokens(),
                latencyMs,
                answerMetadata,
                execution.retrievalResult(),
                execution.webSearchSnippets()
        );
        conversationMemoryRefreshDispatcher.dispatchRefresh(execution.conversationId());
        return response;
    }

    public Flux<ChatStreamEventResponse> streamChat(Long sessionId, AppChatRequest request, AuthenticatedUser user) {
        return Flux.defer(() -> {
                    PreparedChatExecution execution = prepareChatExecution(sessionId, request, user);
                    StringBuilder answerBuilder = new StringBuilder();
                    Instant start = Instant.now();
                    AtomicReference<Integer> promptTokensRef = new AtomicReference<>();
                    AtomicReference<Integer> completionTokensRef = new AtomicReference<>();

                    return conversationChatClient.stream(
                                    execution.chatModel().providerCode(),
                                    execution.chatModel().modelCode(),
                                    execution.conversationId(),
                                    execution.promptMessages(),
                                    execution.historyMessages()
                            )
                            .<ChatStreamEventResponse>handle((chunk, sink) -> {
                                updateUsage(chunk, promptTokensRef, completionTokensRef);
                                String delta = extractStreamText(chunk);
                                if (!StringUtils.hasLength(delta)) {
                                    return;
                                }
                                answerBuilder.append(delta);
                                sink.next(ChatStreamEventResponse.delta(delta));
                            })
                            .concatWith(Mono.fromSupplier(() -> {
                                ChatAnswerMetadata answerMetadata = generateAnswerMetadata(
                                        execution,
                                        request.getQuestion(),
                                        answerBuilder.toString()
                                );
                                ChatResponse response = chatExchangePersistenceService.persistExchange(
                                        execution.session(),
                                        user.getUserId(),
                                        request.getQuestion(),
                                        answerBuilder.toString(),
                                        execution.chatModel().modelId(),
                                          promptTokensRef.get(),
                                          completionTokensRef.get(),
                                          (int) Duration.between(start, Instant.now()).toMillis(),
                                          answerMetadata,
                                          execution.retrievalResult(),
                                          execution.webSearchSnippets()
                                  );
                                  conversationMemoryRefreshDispatcher.dispatchRefresh(execution.conversationId());
                                  return ChatStreamEventResponse.complete(response);
                            }));
                })
                .onErrorResume(ex -> Flux.just(ChatStreamEventResponse.error(resolveStreamErrorMessage(ex))));
    }

    public Flux<ChatStreamEventResponse> regenerateMessage(
            Long messageId,
            AppRegenerateChatMessageRequest request,
            AuthenticatedUser user
    ) {
        return Flux.defer(() -> {
                    ChatMessageEntity message = requireLatestAppMessage(messageId, user.getUserId());
                    PreparedChatExecution execution = prepareChatExecution(
                            message.getSessionId(),
                            message.getQuestionText(),
                            request == null ? null : request.getSelectedKbIds(),
                            request == null ? null : request.getChatModelId(),
                            request == null ? null : request.getWebSearchEnabled(),
                            user,
                            message.getId()
                    );
                    StringBuilder answerBuilder = new StringBuilder();
                    Instant start = Instant.now();
                    AtomicReference<Integer> promptTokensRef = new AtomicReference<>();
                    AtomicReference<Integer> completionTokensRef = new AtomicReference<>();

                    return conversationChatClient.stream(
                                    execution.chatModel().providerCode(),
                                    execution.chatModel().modelCode(),
                                    execution.conversationId(),
                                    execution.promptMessages(),
                                    execution.historyMessages()
                            )
                            .<ChatStreamEventResponse>handle((chunk, sink) -> {
                                updateUsage(chunk, promptTokensRef, completionTokensRef);
                                String delta = extractStreamText(chunk);
                                if (!StringUtils.hasLength(delta)) {
                                    return;
                                }
                                answerBuilder.append(delta);
                                sink.next(ChatStreamEventResponse.delta(delta));
                            })
                            .concatWith(Mono.fromSupplier(() -> {
                                ChatAnswerMetadata answerMetadata = generateAnswerMetadata(
                                        execution,
                                        message.getQuestionText(),
                                        answerBuilder.toString()
                                );
                                ChatResponse response = chatExchangePersistenceService.replaceExchange(
                                        message,
                                        answerBuilder.toString(),
                                        execution.chatModel().modelId(),
                                          promptTokensRef.get(),
                                          completionTokensRef.get(),
                                          (int) Duration.between(start, Instant.now()).toMillis(),
                                          answerMetadata,
                                          execution.retrievalResult(),
                                          execution.webSearchSnippets()
                                  );
                                  conversationMemoryRefreshDispatcher.dispatchRefresh(execution.conversationId());
                                  return ChatStreamEventResponse.complete(response);
                            }));
                })
                .onErrorResume(ex -> Flux.just(ChatStreamEventResponse.error(resolveStreamErrorMessage(ex))));
    }

    private ChatAnswerMetadata generateAnswerMetadata(
            PreparedChatExecution execution,
            String question,
            String answer
    ) {
        return chatAnswerMetadataGenerationService.generate(new ChatAnswerMetadataGenerationRequest(
                execution.chatModel().providerCode(),
                execution.chatModel().modelCode(),
                question,
                answer,
                execution.retrievalResult().chunks().size()
        ));
    }

    @Transactional
    public void submitFeedback(Long messageId, ChatFeedbackRequest request, AuthenticatedUser user) {
        ChatMessageEntity message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("CHAT_MESSAGE_NOT_FOUND", "消息不存在", HttpStatus.NOT_FOUND);
        }
        requireAppSession(message.getSessionId(), user.getUserId());

        ChatFeedbackEntity entity = chatFeedbackMapper.selectOne(new LambdaQueryWrapper<ChatFeedbackEntity>()
                .eq(ChatFeedbackEntity::getMessageId, messageId)
                .eq(ChatFeedbackEntity::getUserId, user.getUserId())
                .last("LIMIT 1"));
        if (entity == null) {
            entity = new ChatFeedbackEntity();
            entity.setMessageId(messageId);
            entity.setUserId(user.getUserId());
            entity.setFeedbackType(request.getFeedbackType());
            entity.setCommentText(request.getComment());
            chatFeedbackMapper.insert(entity);
            return;
        }
        entity.setFeedbackType(request.getFeedbackType());
        entity.setCommentText(request.getComment());
        chatFeedbackMapper.updateById(entity);
    }

    private ChatSessionEntity requireAppSession(Long sessionId, Long userId) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null
                || !session.getUserId().equals(userId)
                || !ChatTerminalTypes.APP.equals(normalizeTerminalType(session.getTerminalType()))) {
            throw new BusinessException("CHAT_SESSION_NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND);
        }
        return session;
    }

    /**
     * 重新生成只支持会话最后一条问答，避免改写历史回答后使后续上下文与摘要失真。
     */
    private ChatMessageEntity requireLatestAppMessage(Long messageId, Long userId) {
        ChatMessageEntity message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("CHAT_MESSAGE_NOT_FOUND", "消息不存在", HttpStatus.NOT_FOUND);
        }
        ChatSessionEntity session = requireAppSession(message.getSessionId(), userId);
        ChatMessageEntity latestMessage = chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, session.getId())
                .orderByDesc(ChatMessageEntity::getId)
                .last("LIMIT 1"));
        if (latestMessage == null || !Objects.equals(latestMessage.getId(), messageId)) {
            throw new BusinessException("CHAT_MESSAGE_REGENERATE_INVALID", "当前仅支持重新生成本会话最后一条回答", HttpStatus.BAD_REQUEST);
        }
        return message;
    }

    private AppChatSessionResponse toSessionResponse(ChatSessionEntity session, List<Long> selectedKbIds) {
        return new AppChatSessionResponse(
                session.getId(),
                session.getKbId(),
                normalizeSceneType(session.getSceneType()),
                session.getSessionName(),
                session.getModelId(),
                Boolean.TRUE.equals(session.getWebSearchEnabled()),
                selectedKbIds,
                session.getStatus()
        );
    }

    private String buildKnowledgeBaseSystemPrompt() {
        return promptTemplateService.load(knowledgeBaseSystemPromptTemplate);
    }

    private String buildGeneralSystemPrompt() {
        return promptTemplateService.load(generalSystemPromptTemplate);
    }

    private String buildKnowledgeBaseUserPrompt(String question, String context, String webSearchContext) {
        boolean hasContext = StringUtils.hasText(context);
        boolean hasWebSearchContext = StringUtils.hasText(webSearchContext);
        Map<String, String> variables = Map.of(
                "temporal_context_block", buildTemporalContextBlock(question, hasWebSearchContext),
                "knowledge_context", defaultText(context),
                "web_search_context", defaultText(webSearchContext),
                "question", defaultText(question)
        );
        if (hasContext && hasWebSearchContext) {
            return promptTemplateService.render(knowledgeBaseUserContextWithWebPromptTemplate, variables);
        }
        if (hasContext) {
            return promptTemplateService.render(knowledgeBaseUserContextOnlyPromptTemplate, variables);
        }
        if (hasWebSearchContext) {
            return promptTemplateService.render(knowledgeBaseUserWebOnlyPromptTemplate, variables);
        }
        return promptTemplateService.render(knowledgeBaseUserNoContextPromptTemplate, variables);
    }

    private String buildGeneralUserPrompt(String question, String webSearchContext) {
        String temporalContext = buildTemporalContext(question, StringUtils.hasText(webSearchContext));
        String temporalContextBlock = withTrailingBlankLine(temporalContext);
        if (!StringUtils.hasText(webSearchContext)) {
            if (!StringUtils.hasText(temporalContext)) {
                return question;
            }
            return promptTemplateService.render(generalUserPlainPromptTemplate, Map.of(
                    "temporal_context_block", temporalContextBlock,
                    "question", defaultText(question)
            ));
        }
        return promptTemplateService.render(generalUserWithWebPromptTemplate, Map.of(
                "temporal_context_block", temporalContextBlock,
                "web_search_context", defaultText(webSearchContext),
                "question", defaultText(question)
        ));
    }

    private String buildWebSearchContext(List<WebSearchSnippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return "";
        }
        int maxChars = Math.max(200, webSearchProperties.getContextMaxChars());
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < snippets.size(); index++) {
            WebSearchSnippet snippet = snippets.get(index);
            if (snippet == null) {
                continue;
            }
            StringBuilder sectionBuilder = new StringBuilder();
            sectionBuilder.append("结果").append(index + 1).append("：\n");
            if (StringUtils.hasText(snippet.title())) {
                sectionBuilder.append("标题：").append(snippet.title()).append("\n");
            }
            if (StringUtils.hasText(snippet.snippet())) {
                sectionBuilder.append("摘要：").append(snippet.snippet()).append("\n");
            }
            if (StringUtils.hasText(snippet.url())) {
                sectionBuilder.append("链接：").append(snippet.url()).append("\n");
            }
            if (snippet.publishedAt() != null) {
                sectionBuilder.append("时间：").append(formatPublishedAt(snippet.publishedAt())).append("\n");
            }
            sectionBuilder.append("\n");
            String section = sectionBuilder.toString();
            if (builder.length() + section.length() > maxChars) {
                int remain = maxChars - builder.length();
                if (remain <= 0) {
                    break;
                }
                builder.append(abbreviateForContext(section, remain));
                break;
            }
            builder.append(section);
        }
        return builder.toString().trim();
    }

    private List<ChatPromptMessage> buildHistoryMessages(Long sessionId, Long beforeMessageIdExclusive) {
        List<ChatMessageEntity> history = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .lt(beforeMessageIdExclusive != null, ChatMessageEntity::getId, beforeMessageIdExclusive)
                .orderByAsc(ChatMessageEntity::getId));
        if (history.isEmpty()) {
            return List.of();
        }
        List<ChatPromptMessage> messages = new ArrayList<>(history.size() * 2);
        for (ChatMessageEntity item : history) {
            if (StringUtils.hasText(item.getQuestionText())) {
                messages.add(new ChatPromptMessage("user", item.getQuestionText()));
            }
            if (StringUtils.hasText(item.getAnswerText())) {
                messages.add(new ChatPromptMessage("assistant", item.getAnswerText()));
            }
        }
        return messages;
    }

    private PreparedChatExecution prepareChatExecution(Long sessionId, AppChatRequest request, AuthenticatedUser user) {
        return prepareChatExecution(
                sessionId,
                request.getQuestion(),
                request.getSelectedKbIds(),
                request.getChatModelId(),
                request.getWebSearchEnabled(),
                user,
                null
        );
    }

    private PreparedChatExecution prepareChatExecution(
            Long sessionId,
            String question,
            List<Long> requestedSelectedKbIds,
            Long requestedChatModelId,
            Boolean requestedWebSearchEnabled,
            AuthenticatedUser user,
            Long historyBeforeMessageIdExclusive
    ) {
        ChatSessionEntity session = requireAppSession(sessionId, user.getUserId());
        String sceneType = normalizeSceneType(session.getSceneType());

        List<Long> effectiveSelectedKbIds = requestedSelectedKbIds == null
                ? loadSelectedKnowledgeBaseIds(session.getId()).getOrDefault(session.getId(), defaultSelectedKnowledgeBaseIds(session))
                : normalizeSelectedKnowledgeBaseIds(sceneType, session.getKbId(), requestedSelectedKbIds);
        if (requestedSelectedKbIds != null) {
            replaceSessionKnowledgeBaseRelations(session.getId(), effectiveSelectedKbIds);
        }

        boolean effectiveWebSearchAvailable = isWebSearchAvailable(requestedWebSearchEnabled);
        refreshSessionPreferences(session, requestedChatModelId, requestedWebSearchEnabled);
        ModelService.ChatModelDescriptor chatModel = resolveChatModel(session, requestedChatModelId);
        var executionPlan = chatExecutionPlanningService.plan(new ChatExecutionPlanningRequest(
                chatModel.providerCode(),
                chatModel.modelCode(),
                question,
                !effectiveSelectedKbIds.isEmpty(),
                effectiveWebSearchAvailable,
                effectiveSelectedKbIds.size(),
                ChatSceneTypes.KNOWLEDGE_BASE.equals(sceneType)
        ));
        logWebSearchDecision(
                session.getId(),
                sceneType,
                requestedWebSearchEnabled,
                effectiveWebSearchAvailable,
                executionPlan.needWebSearch(),
                executionPlan.webSearchQuery()
        );

        List<WebSearchSnippet> webSearchSnippets = loadWebSearchSnippets(
                executionPlan.webSearchQuery(),
                effectiveWebSearchAvailable && executionPlan.needWebSearch()
        );

        RetrievalService.RetrievalResult retrievalResult = !executionPlan.needRetrieval() || effectiveSelectedKbIds.isEmpty()
                ? new RetrievalService.RetrievalResult(List.of(), "")
                : retrievalService.retrieveAcrossKnowledgeBases(
                effectiveSelectedKbIds.stream().map(knowledgeBaseService::requireById).toList(),
                executionPlan.retrievalQuery()
        );

        List<ChatPromptMessage> promptMessages = buildPromptMessages(
                sceneType,
                question,
                retrievalResult,
                effectiveSelectedKbIds,
                webSearchSnippets
        );

        return new PreparedChatExecution(
                session,
                chatModel,
                retrievalResult,
                webSearchSnippets,
                promptMessages,
                buildHistoryMessages(session.getId(), historyBeforeMessageIdExclusive),
                conversationIdCodec.encode(session)
        );
    }

    private void updateUsage(
            org.springframework.ai.chat.model.ChatResponse chunk,
            AtomicReference<Integer> promptTokensRef,
            AtomicReference<Integer> completionTokensRef
    ) {
        if (chunk == null || chunk.getMetadata() == null || chunk.getMetadata().getUsage() == null) {
            return;
        }
        if (chunk.getMetadata().getUsage().getPromptTokens() != null) {
            promptTokensRef.set(chunk.getMetadata().getUsage().getPromptTokens());
        }
        if (chunk.getMetadata().getUsage().getCompletionTokens() != null) {
            completionTokensRef.set(chunk.getMetadata().getUsage().getCompletionTokens());
        }
    }

    private String extractStreamText(org.springframework.ai.chat.model.ChatResponse chunk) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return "";
        }
        String text = chunk.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private String resolveStreamErrorMessage(Throwable ex) {
        return AiProviderExceptionSupport.resolveUserMessage(ex, "问答服务");
    }

    private com.ragadmin.server.chat.dto.ChatAnswerMetadataResponse toMetadataResponse(ChatMessageEntity message) {
        if (message == null) {
            return null;
        }
        if (!StringUtils.hasText(message.getAnswerConfidence())
                && message.getHasKnowledgeBaseEvidence() == null
                && message.getNeedFollowUp() == null) {
            return null;
        }
        return new com.ragadmin.server.chat.dto.ChatAnswerMetadataResponse(
                message.getAnswerConfidence(),
                Boolean.TRUE.equals(message.getHasKnowledgeBaseEvidence()),
                Boolean.TRUE.equals(message.getNeedFollowUp())
        );
    }

    private String normalizeSceneType(String sceneType) {
        if (!StringUtils.hasText(sceneType)) {
            return ChatSceneTypes.KNOWLEDGE_BASE;
        }
        String normalized = sceneType.trim().toUpperCase();
        if (ChatSceneTypes.GENERAL.equals(normalized) || ChatSceneTypes.KNOWLEDGE_BASE.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException("CHAT_SCENE_INVALID", "会话场景不合法", HttpStatus.BAD_REQUEST);
    }

    private String normalizeOptionalSceneType(String sceneType) {
        if (!StringUtils.hasText(sceneType)) {
            return null;
        }
        return normalizeSceneType(sceneType);
    }

    private String normalizeAppSceneType(String sceneType, Long kbId) {
        if (!StringUtils.hasText(sceneType)) {
            return kbId == null ? ChatSceneTypes.GENERAL : ChatSceneTypes.KNOWLEDGE_BASE;
        }
        return normalizeSceneType(sceneType);
    }

    private String normalizeTerminalType(String terminalType) {
        if (!StringUtils.hasText(terminalType)) {
            return ChatTerminalTypes.ADMIN;
        }
        String normalized = terminalType.trim().toUpperCase();
        if (ChatTerminalTypes.ADMIN.equals(normalized) || ChatTerminalTypes.APP.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException("CHAT_TERMINAL_INVALID", "会话终端类型不合法", HttpStatus.BAD_REQUEST);
    }

    private Long requireKnowledgeBaseId(Long kbId) {
        if (kbId == null) {
            throw new BusinessException("KB_NOT_FOUND", "知识库不存在", HttpStatus.BAD_REQUEST);
        }
        return kbId;
    }

    private List<Long> normalizeSelectedKnowledgeBaseIds(String sceneType, Long anchorKbId, List<Long> selectedKbIds) {
        LinkedHashSet<Long> orderedIds = new LinkedHashSet<>();
        if (selectedKbIds != null) {
            orderedIds.addAll(selectedKbIds.stream().filter(Objects::nonNull).toList());
        }
        if (ChatSceneTypes.KNOWLEDGE_BASE.equals(sceneType)) {
            orderedIds.add(requireKnowledgeBaseId(anchorKbId));
        }
        List<Long> normalized = orderedIds.stream().toList();
        normalized.forEach(knowledgeBaseService::requireById);
        return normalized;
    }

    private void replaceSessionKnowledgeBaseRelations(Long sessionId, List<Long> selectedKbIds) {
        chatSessionKnowledgeBaseRelMapper.delete(new LambdaQueryWrapper<ChatSessionKnowledgeBaseRelEntity>()
                .eq(ChatSessionKnowledgeBaseRelEntity::getSessionId, sessionId));
        if (selectedKbIds == null || selectedKbIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < selectedKbIds.size(); i++) {
            ChatSessionKnowledgeBaseRelEntity relEntity = new ChatSessionKnowledgeBaseRelEntity();
            relEntity.setSessionId(sessionId);
            relEntity.setKbId(selectedKbIds.get(i));
            relEntity.setSortNo(i + 1);
            chatSessionKnowledgeBaseRelMapper.insert(relEntity);
        }
    }

    private Map<Long, List<Long>> loadSelectedKnowledgeBaseIds(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        return chatSessionKnowledgeBaseRelMapper.selectList(new LambdaQueryWrapper<ChatSessionKnowledgeBaseRelEntity>()
                        .in(ChatSessionKnowledgeBaseRelEntity::getSessionId, sessionIds)
                        .orderByAsc(ChatSessionKnowledgeBaseRelEntity::getSessionId, ChatSessionKnowledgeBaseRelEntity::getSortNo))
                .stream()
                .collect(Collectors.groupingBy(
                        ChatSessionKnowledgeBaseRelEntity::getSessionId,
                        Collectors.mapping(ChatSessionKnowledgeBaseRelEntity::getKbId, Collectors.toList())
                ));
    }

    private Map<Long, List<Long>> loadSelectedKnowledgeBaseIds(Long sessionId) {
        return loadSelectedKnowledgeBaseIds(List.of(sessionId));
    }

    private List<Long> defaultSelectedKnowledgeBaseIds(ChatSessionEntity session) {
        if (session.getKbId() == null) {
            return List.of();
        }
        return List.of(session.getKbId());
    }

    private void refreshSessionPreferences(ChatSessionEntity session, Long modelId, Boolean webSearchEnabled) {
        boolean changed = false;
        if (modelId != null && !Objects.equals(modelId, session.getModelId())) {
            session.setModelId(modelId);
            changed = true;
        }
        if (webSearchEnabled != null && !Objects.equals(Boolean.TRUE.equals(webSearchEnabled), Boolean.TRUE.equals(session.getWebSearchEnabled()))) {
            session.setWebSearchEnabled(Boolean.TRUE.equals(webSearchEnabled));
            changed = true;
        }
        if (changed) {
            chatSessionMapper.updateById(session);
        }
    }

    private ModelService.ChatModelDescriptor resolveChatModel(
            ChatSessionEntity session,
            Long requestChatModelId
    ) {
        if (requestChatModelId != null) {
            return modelService.resolveChatModelDescriptor(requestChatModelId);
        }
        if (session.getModelId() != null) {
            return modelService.resolveChatModelDescriptor(session.getModelId());
        }
        return modelService.resolveChatModelDescriptor(null);
    }

    private List<ChatPromptMessage> buildPromptMessages(
            String sceneType,
            String question,
            RetrievalService.RetrievalResult retrievalResult,
            List<Long> selectedKbIds,
            List<WebSearchSnippet> webSearchSnippets
    ) {
        String webSearchContext = buildWebSearchContext(webSearchSnippets);
        if (!selectedKbIds.isEmpty() || ChatSceneTypes.KNOWLEDGE_BASE.equals(sceneType)) {
            return List.of(
                    new ChatPromptMessage("system", buildKnowledgeBaseSystemPrompt()),
                    new ChatPromptMessage("user", buildKnowledgeBaseUserPrompt(question, retrievalResult.context(), webSearchContext))
            );
        }
        return List.of(
                new ChatPromptMessage("system", buildGeneralSystemPrompt()),
                new ChatPromptMessage("user", buildGeneralUserPrompt(question, webSearchContext))
        );
    }

    private List<WebSearchSnippet> loadWebSearchSnippets(String question, boolean webSearchEnabled) {
        if (!webSearchEnabled || !StringUtils.hasText(question)) {
            return List.of();
        }
        String resolvedQuery = normalizeWebSearchQuery(question);
        int resolvedTopK = resolveWebSearchTopK();
        long startNanos = System.nanoTime();
        try {
            List<WebSearchSnippet> snippets = webSearchProvider.search(resolvedQuery, resolvedTopK);
            List<WebSearchSnippet> safeSnippets = snippets == null ? List.of() : snippets.stream().filter(Objects::nonNull).toList();
            log.info(
                    "前台联网搜索摘要已加载，query={}, resolvedQuery={}, queryLength={}, topK={}, resultCount={}, latencyMs={}",
                    abbreviateForLog(question),
                    abbreviateForLog(resolvedQuery),
                    resolvedQuery.length(),
                    resolvedTopK,
                    safeSnippets.size(),
                    (System.nanoTime() - startNanos) / 1_000_000
            );
            return safeSnippets;
        } catch (Exception ex) {
            log.warn(
                    "前台联网搜索已降级为空结果，query={}, resolvedQuery={}, queryLength={}, topK={}, reason={}",
                    abbreviateForLog(question),
                    abbreviateForLog(resolvedQuery),
                    resolvedQuery.length(),
                    resolvedTopK,
                    ex.getClass().getSimpleName(),
                    ex
            );
            return List.of();
        }
    }

    private boolean isWebSearchAvailable(Boolean requestedWebSearchEnabled) {
        boolean available = Boolean.TRUE.equals(requestedWebSearchEnabled)
                && webSearchProvider != null
                && webSearchProvider.isAvailable();
        if (Boolean.TRUE.equals(requestedWebSearchEnabled) && !available) {
            log.info("前台联网搜索当前不可用，reason=provider_unavailable");
        }
        return available;
    }

    private int resolveWebSearchTopK() {
        return Math.max(1, webSearchProperties.getDefaultTopK());
    }

    private void logWebSearchDecision(
            Long sessionId,
            String sceneType,
            Boolean requestedWebSearchEnabled,
            boolean providerAvailable,
            boolean needWebSearch,
            String webSearchQuery
    ) {
        if (!Boolean.TRUE.equals(requestedWebSearchEnabled)) {
            return;
        }
        log.info(
                "前台问答联网决策，sessionId={}, sceneType={}, providerAvailable={}, needWebSearch={}, searchQuery={}",
                sessionId,
                sceneType,
                providerAvailable,
                needWebSearch,
                abbreviateForLog(webSearchQuery)
        );
    }

    private String abbreviateForLog(String text) {
        return abbreviate(text, Math.max(20, webSearchProperties.getLogQueryMaxChars()));
    }

    private String abbreviateForContext(String text, int maxChars) {
        return abbreviate(text, Math.max(1, maxChars));
    }

    private String abbreviate(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        if (maxChars <= 3) {
            return normalized.substring(0, maxChars);
        }
        return normalized.substring(0, maxChars - 3) + "...";
    }

    private String buildTemporalContext(String question, boolean hasWebSearchContext) {
        if (!hasWebSearchContext && !containsRelativeDateExpression(question)) {
            return "";
        }
        Clock appClock = clock.withZone(APP_ZONE_ID);
        LocalDate today = LocalDate.now(appClock);
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        LocalDateTime now = LocalDateTime.now(appClock);

        StringBuilder builder = new StringBuilder();
        builder.append("时间上下文（北京时间）：\n")
                .append("- 当前日期：").append(today.format(DATE_FORMATTER)).append("\n")
                .append("- 当前时间：").append(now.format(DATE_TIME_FORMATTER)).append("\n");
        if (containsRelativeDateExpression(question)) {
            builder.append("- 本轮问题中的“今天”默认指：").append(today.format(DATE_FORMATTER)).append("\n")
                    .append("- 本轮问题中的“昨天”默认指：").append(yesterday.format(DATE_FORMATTER)).append("\n")
                    .append("- 本轮问题中的“明天”默认指：").append(tomorrow.format(DATE_FORMATTER)).append("\n");
        }
        if (hasWebSearchContext) {
            builder.append("- 如果联网摘要包含多天信息，优先回答与上述日期最匹配的一天；只有在证据明显冲突时，才说明冲突。");
        }
        return builder.toString();
    }

    private boolean containsRelativeDateExpression(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("今天")
                || text.contains("今日")
                || text.contains("明天")
                || text.contains("明日")
                || text.contains("昨天")
                || text.contains("昨日");
    }

    private String normalizeWebSearchQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        Clock appClock = clock.withZone(APP_ZONE_ID);
        LocalDate today = LocalDate.now(appClock);
        String normalized = query.trim();
        normalized = replaceRelativeDateKeyword(normalized, "今天", today);
        normalized = replaceRelativeDateKeyword(normalized, "今日", today);
        normalized = replaceRelativeDateKeyword(normalized, "明天", today.plusDays(1));
        normalized = replaceRelativeDateKeyword(normalized, "明日", today.plusDays(1));
        normalized = replaceRelativeDateKeyword(normalized, "昨天", today.minusDays(1));
        normalized = replaceRelativeDateKeyword(normalized, "昨日", today.minusDays(1));
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String replaceRelativeDateKeyword(String query, String keyword, LocalDate date) {
        if (!StringUtils.hasText(query) || !query.contains(keyword)) {
            return query;
        }
        return query.replace(keyword, " " + date.format(DATE_FORMATTER) + " ");
    }

    private String formatPublishedAt(Instant publishedAt) {
        return DATE_TIME_FORMATTER.withZone(APP_ZONE_ID).format(publishedAt);
    }

    private String buildTemporalContextBlock(String question, boolean hasWebSearchContext) {
        return withTrailingBlankLine(buildTemporalContext(question, hasWebSearchContext));
    }

    private String withTrailingBlankLine(String text) {
        return StringUtils.hasText(text) ? text + "\n\n" : "";
    }

    private String defaultText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private record PreparedChatExecution(
            ChatSessionEntity session,
            ModelService.ChatModelDescriptor chatModel,
            RetrievalService.RetrievalResult retrievalResult,
            List<WebSearchSnippet> webSearchSnippets,
            List<ChatPromptMessage> promptMessages,
            List<ChatPromptMessage> historyMessages,
            String conversationId
    ) {
    }

    private WebSearchSourceResponse toWebSearchSourceResponse(ChatWebSearchSourceEntity entity) {
        return new WebSearchSourceResponse(
                entity.getTitle(),
                entity.getSourceUrl(),
                entity.getPublishedAt() == null ? null : entity.getPublishedAt().atOffset(ZoneOffset.UTC).toInstant(),
                entity.getSnippet()
        );
    }

    private Map<Long, String> resolveDocumentNames(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ChunkEntity> chunkMap = chunkMapper.selectBatchIds(chunkIds)
                .stream()
                .collect(Collectors.toMap(ChunkEntity::getId, java.util.function.Function.identity()));
        List<Long> documentIds = chunkMap.values().stream()
                .map(ChunkEntity::getDocumentId)
                .distinct()
                .toList();
        // 分段已被删或引用失效时，这里可能拿不到任何文档 ID，避免继续触发空批量查询。
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return documentMapper.selectBatchIds(documentIds)
                .stream()
                .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getDocName));
    }

    private ChatReferenceResponse toReferenceResponse(
            ChatAnswerReferenceEntity ref,
            ChunkEntity chunk,
            Map<Long, String> documentNameMap
    ) {
        if (chunk == null) {
            return new ChatReferenceResponse(null, null, null, ref.getChunkId(), null, ref.getScore() == null ? 0D : ref.getScore().doubleValue(), "");
        }
        return new ChatReferenceResponse(
                chunk.getKbId(),
                chunk.getDocumentId(),
                documentNameMap.get(chunk.getDocumentId()),
                ref.getChunkId(),
                chunk.getChunkNo(),
                ref.getScore() == null ? 0D : ref.getScore().doubleValue(),
                chunk.getChunkText() == null ? "" : chunk.getChunkText()
        );
    }
}
