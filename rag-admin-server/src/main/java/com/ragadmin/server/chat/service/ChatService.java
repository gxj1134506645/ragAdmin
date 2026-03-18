package com.ragadmin.server.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.chat.dto.ChatRequest;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatSessionResponse;
import com.ragadmin.server.chat.dto.ChatStreamEventResponse;
import com.ragadmin.server.chat.dto.ChatUsageResponse;
import com.ragadmin.server.chat.dto.CreateChatSessionRequest;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatFeedbackEntity;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatFeedbackMapper;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.service.RetrievalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatAnswerReferenceMapper chatAnswerReferenceMapper;

    @Autowired
    private ChatFeedbackMapper chatFeedbackMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private ConversationChatClient conversationChatClient;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private ChunkMapper chunkMapper;

    @Autowired
    private ChatExchangePersistenceService chatExchangePersistenceService;

    public ChatSessionResponse createSession(CreateChatSessionRequest request, AuthenticatedUser user) {
        String sceneType = normalizeSceneType(request.getSceneType());
        if (ChatSceneTypes.GENERAL.equals(sceneType)) {
            validateGeneralSceneCreateRequest(request);
            ChatSessionEntity existingGeneralSession = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSessionEntity>()
                    .eq(ChatSessionEntity::getUserId, user.getUserId())
                    .eq(ChatSessionEntity::getSceneType, ChatSceneTypes.GENERAL)
                    .last("LIMIT 1"));
            if (existingGeneralSession != null) {
                return toSessionResponse(existingGeneralSession);
            }
        } else {
            requireKnowledgeBaseId(request.getKbId());
            knowledgeBaseService.requireById(request.getKbId());
        }

        ChatSessionEntity session = new ChatSessionEntity();
        session.setKbId(ChatSceneTypes.KNOWLEDGE_BASE.equals(sceneType) ? request.getKbId() : null);
        session.setUserId(user.getUserId());
        session.setSceneType(sceneType);
        session.setSessionName(request.getSessionName());
        session.setStatus("ENABLED");
        chatSessionMapper.insert(session);
        return toSessionResponse(session);
    }

    public PageResponse<ChatSessionResponse> listSessions(Long kbId, String sceneType, AuthenticatedUser user, long pageNo, long pageSize) {
        String normalizedSceneType = normalizeOptionalSceneType(sceneType);
        Page<ChatSessionEntity> page = chatSessionMapper.selectPage(Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getUserId, user.getUserId())
                        .eq(StringUtils.hasText(normalizedSceneType), ChatSessionEntity::getSceneType, normalizedSceneType)
                        .eq(kbId != null, ChatSessionEntity::getKbId, kbId)
                        .orderByDesc(ChatSessionEntity::getId));
        return new PageResponse<>(page.getRecords().stream().map(this::toSessionResponse).toList(), pageNo, pageSize, page.getTotal());
    }

    public List<ChatMessageResponse> listMessages(Long sessionId, AuthenticatedUser user) {
        ChatSessionEntity session = requireSession(sessionId, user.getUserId());
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, session.getId())
                .orderByAsc(ChatMessageEntity::getId));
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ChatAnswerReferenceEntity>> refsByMessageId = chatAnswerReferenceMapper.selectList(new LambdaQueryWrapper<ChatAnswerReferenceEntity>()
                        .in(ChatAnswerReferenceEntity::getMessageId, messages.stream().map(ChatMessageEntity::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(ChatAnswerReferenceEntity::getMessageId));
        Map<Long, String> documentNameMap = resolveDocumentNames(refsByMessageId.values().stream()
                .flatMap(List::stream)
                .map(ChatAnswerReferenceEntity::getChunkId)
                .toList());
        Map<Long, ChunkEntity> chunkMap = chunkMapper.selectBatchIds(refsByMessageId.values().stream()
                        .flatMap(List::stream)
                        .map(ChatAnswerReferenceEntity::getChunkId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ChunkEntity::getId, java.util.function.Function.identity()));

        return messages.stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getQuestionText(),
                        message.getAnswerText(),
                        refsByMessageId.getOrDefault(message.getId(), List.of()).stream()
                                .map(ref -> toReferenceResponse(ref, chunkMap.get(ref.getChunkId()), documentNameMap))
                                .toList()
                ))
                .toList();
    }

    @Transactional
    public ChatResponse chat(Long sessionId, ChatRequest request, AuthenticatedUser user) {
        PreparedChatExecution execution = prepareChatExecution(sessionId, request, user);

        Instant start = Instant.now();
        ChatModelClient.ChatCompletionResult completion = conversationChatClient.chat(
                execution.chatModel().providerCode(),
                execution.chatModel().modelCode(),
                execution.conversationId(),
                execution.promptMessages(),
                execution.historyMessages()
        );
        int latencyMs = (int) Duration.between(start, Instant.now()).toMillis();

        return chatExchangePersistenceService.persistExchange(
                execution.session(),
                user.getUserId(),
                request.getQuestion(),
                completion.content(),
                execution.chatModel().modelId(),
                completion.promptTokens(),
                completion.completionTokens(),
                latencyMs,
                execution.retrievalResult()
        );
    }

    public Flux<ChatStreamEventResponse> streamChat(Long sessionId, ChatRequest request, AuthenticatedUser user) {
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
                .map(chunk -> {
                    updateUsage(chunk, promptTokensRef, completionTokensRef);
                    String delta = extractStreamText(chunk);
                    if (!StringUtils.hasLength(delta)) {
                        return null;
                    }
                    answerBuilder.append(delta);
                    return ChatStreamEventResponse.delta(delta);
                })
                .filter(Objects::nonNull)
                .concatWith(Mono.fromSupplier(() -> {
                    ChatResponse response = chatExchangePersistenceService.persistExchange(
                            execution.session(),
                            user.getUserId(),
                            request.getQuestion(),
                            answerBuilder.toString(),
                            execution.chatModel().modelId(),
                            promptTokensRef.get(),
                            completionTokensRef.get(),
                            (int) Duration.between(start, Instant.now()).toMillis(),
                            execution.retrievalResult()
                    );
                    return ChatStreamEventResponse.complete(response);
                }))
                .onErrorResume(ex -> Flux.just(ChatStreamEventResponse.error(resolveStreamErrorMessage(ex))));
    }

    @Transactional
    public void submitFeedback(Long messageId, String feedbackType, String comment, AuthenticatedUser user) {
        ChatMessageEntity message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("CHAT_MESSAGE_NOT_FOUND", "消息不存在", HttpStatus.NOT_FOUND);
        }
        ChatSessionEntity session = requireSession(message.getSessionId(), user.getUserId());
        if (session == null) {
            throw new BusinessException("CHAT_SESSION_NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND);
        }

        ChatFeedbackEntity entity = chatFeedbackMapper.selectOne(new LambdaQueryWrapper<ChatFeedbackEntity>()
                .eq(ChatFeedbackEntity::getMessageId, messageId)
                .eq(ChatFeedbackEntity::getUserId, user.getUserId())
                .last("LIMIT 1"));
        if (entity == null) {
            entity = new ChatFeedbackEntity();
            entity.setMessageId(messageId);
            entity.setUserId(user.getUserId());
            entity.setFeedbackType(feedbackType);
            entity.setCommentText(comment);
            chatFeedbackMapper.insert(entity);
            return;
        }
        entity.setFeedbackType(feedbackType);
        entity.setCommentText(comment);
        chatFeedbackMapper.updateById(entity);
    }

    private ChatSessionEntity requireSession(Long sessionId, Long userId) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("CHAT_SESSION_NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND);
        }
        return session;
    }

    private ChatSessionResponse toSessionResponse(ChatSessionEntity session) {
        return new ChatSessionResponse(session.getId(), session.getKbId(), normalizeSceneType(session.getSceneType()), session.getSessionName(), session.getStatus());
    }

    private String buildKnowledgeBaseSystemPrompt() {
        return "你是企业知识库问答助手。只能基于提供的知识片段回答，无法确认时要明确说明。";
    }

    private String buildGeneralSystemPrompt() {
        return "你是企业智能助手。优先给出直接、准确、可执行的回答；如果信息不充分，要明确说明你的判断边界。";
    }

    private String buildKnowledgeBaseUserPrompt(String question, String context) {
        if (context == null || context.isBlank()) {
            return "问题：\n" + question + "\n\n当前没有命中知识片段，请明确说明无法从知识库确认答案。";
        }
        return "知识片段：\n" + context + "\n\n问题：\n" + question + "\n\n请基于知识片段回答，并尽量简洁。";
    }

    /**
     * 会话记忆需要显式区分场景和知识库，避免首页通用会话、不同知识库会话之间互相污染。
     */
    private String buildConversationId(ChatSessionEntity session) {
        String sceneType = normalizeSceneType(session.getSceneType());
        if (ChatSceneTypes.GENERAL.equals(sceneType)) {
            return "chat-scene-home-user-" + session.getUserId();
        }
        return "chat-scene-kb-user-" + session.getUserId()
                + "-kb-" + requireKnowledgeBaseId(session.getKbId())
                + "-session-" + session.getId();
    }

    /**
     * 旧会话首次切到 Spring AI memory 时，按历史问答补种 USER / ASSISTANT 消息。
     */
    private List<ChatModelClient.ChatMessage> buildHistoryMessages(Long sessionId) {
        List<ChatMessageEntity> history = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getId));
        if (history.isEmpty()) {
            return List.of();
        }
        List<ChatModelClient.ChatMessage> messages = new ArrayList<>(history.size() * 2);
        for (ChatMessageEntity item : history) {
            if (StringUtils.hasText(item.getQuestionText())) {
                messages.add(new ChatModelClient.ChatMessage("user", item.getQuestionText()));
            }
            if (StringUtils.hasText(item.getAnswerText())) {
                messages.add(new ChatModelClient.ChatMessage("assistant", item.getAnswerText()));
            }
        }
        return messages;
    }

    private PreparedChatExecution prepareChatExecution(Long sessionId, ChatRequest request, AuthenticatedUser user) {
        ChatSessionEntity session = requireSession(sessionId, user.getUserId());
        String sceneType = normalizeSceneType(session.getSceneType());

        RetrievalService.RetrievalResult retrievalResult;
        ModelService.ChatModelDescriptor chatModel;
        List<ChatModelClient.ChatMessage> promptMessages;
        if (ChatSceneTypes.KNOWLEDGE_BASE.equals(sceneType)) {
            Long kbId = requireKnowledgeBaseId(session.getKbId());
            if (!kbId.equals(request.getKbId())) {
                throw new BusinessException("CHAT_KB_MISMATCH", "会话与知识库不匹配", HttpStatus.BAD_REQUEST);
            }
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(kbId);
            retrievalResult = retrievalService.retrieve(knowledgeBase, request.getQuestion());
            chatModel = modelService.resolveChatModelDescriptor(knowledgeBase.getChatModelId());
            promptMessages = List.of(
                    new ChatModelClient.ChatMessage("system", buildKnowledgeBaseSystemPrompt()),
                    new ChatModelClient.ChatMessage("user", buildKnowledgeBaseUserPrompt(request.getQuestion(), retrievalResult.context()))
            );
        } else {
            if (request.getKbId() != null) {
                throw new BusinessException("CHAT_SCENE_INVALID", "首页通用会话不允许携带知识库标识", HttpStatus.BAD_REQUEST);
            }
            retrievalResult = new RetrievalService.RetrievalResult(List.of(), "");
            chatModel = modelService.resolveChatModelDescriptor(null);
            promptMessages = List.of(
                    new ChatModelClient.ChatMessage("system", buildGeneralSystemPrompt()),
                    new ChatModelClient.ChatMessage("user", request.getQuestion())
            );
        }
        return new PreparedChatExecution(
                session,
                chatModel,
                retrievalResult,
                promptMessages,
                buildHistoryMessages(session.getId()),
                buildConversationId(session)
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
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message : "流式问答失败";
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

    private Long requireKnowledgeBaseId(Long kbId) {
        if (kbId == null) {
            throw new BusinessException("KB_NOT_FOUND", "知识库不存在", HttpStatus.BAD_REQUEST);
        }
        return kbId;
    }

    private void validateGeneralSceneCreateRequest(CreateChatSessionRequest request) {
        if (request.getKbId() != null) {
            throw new BusinessException("CHAT_SCENE_INVALID", "首页通用会话不允许绑定知识库", HttpStatus.BAD_REQUEST);
        }
    }

    private record PreparedChatExecution(
            ChatSessionEntity session,
            ModelService.ChatModelDescriptor chatModel,
            RetrievalService.RetrievalResult retrievalResult,
            List<ChatModelClient.ChatMessage> promptMessages,
            List<ChatModelClient.ChatMessage> historyMessages,
            String conversationId
    ) {
    }

    private Map<Long, String> resolveDocumentNames(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ChunkEntity> chunkMap = chunkMapper.selectBatchIds(chunkIds)
                .stream()
                .collect(Collectors.toMap(ChunkEntity::getId, java.util.function.Function.identity()));
        return documentMapper.selectBatchIds(chunkMap.values().stream()
                        .map(ChunkEntity::getDocumentId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getDocName));
    }

    private ChatReferenceResponse toReferenceResponse(
            ChatAnswerReferenceEntity ref,
            ChunkEntity chunk,
            Map<Long, String> documentNameMap
    ) {
        if (chunk == null) {
            return new ChatReferenceResponse(null, null, ref.getChunkId(), ref.getScore() == null ? 0D : ref.getScore().doubleValue(), "");
        }
        return new ChatReferenceResponse(
                chunk.getDocumentId(),
                documentNameMap.get(chunk.getDocumentId()),
                ref.getChunkId(),
                ref.getScore() == null ? 0D : ref.getScore().doubleValue(),
                chunk.getChunkText() == null ? "" : chunk.getChunkText()
        );
    }
}
