package com.ragadmin.server.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.app.dto.AppChatRequest;
import com.ragadmin.server.app.dto.AppChatSessionResponse;
import com.ragadmin.server.app.dto.AppCreateChatSessionRequest;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.chat.ChatSceneTypes;
import com.ragadmin.server.chat.ChatTerminalTypes;
import com.ragadmin.server.chat.dto.ChatFeedbackRequest;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatStreamEventResponse;
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
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.DocumentEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
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

    private static final int WEB_SEARCH_TOP_K = 5;

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
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private ConversationChatClient conversationChatClient;

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
    private WebSearchProvider webSearchProvider;

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
        Map<Long, ChatFeedbackEntity> feedbackByMessageId = chatFeedbackMapper.selectList(new LambdaQueryWrapper<ChatFeedbackEntity>()
                        .in(ChatFeedbackEntity::getMessageId, messages.stream().map(ChatMessageEntity::getId).toList())
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
                            refsByMessageId.getOrDefault(message.getId(), List.of()).stream()
                                    .map(ref -> toReferenceResponse(ref, chunkMap.get(ref.getChunkId()), documentNameMap))
                                    .toList(),
                            feedback == null ? null : feedback.getFeedbackType(),
                            feedback == null ? null : feedback.getCommentText()
                    );
                })
                .toList();
    }

    @Transactional
    public AppChatSessionResponse renameSession(Long sessionId, String sessionName, AuthenticatedUser user) {
        ChatSessionEntity session = requireAppSession(sessionId, user.getUserId());
        String normalizedSessionName = sessionName.trim();
        if (!Objects.equals(normalizedSessionName, session.getSessionName())) {
            session.setSessionName(normalizedSessionName);
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
        ChatModelClient.ChatCompletionResult completion = conversationChatClient.chat(
                execution.chatModel().providerCode(),
                execution.chatModel().modelCode(),
                execution.conversationId(),
                execution.promptMessages(),
                execution.historyMessages()
        );
        int latencyMs = (int) Duration.between(start, Instant.now()).toMillis();

        ChatResponse response = chatExchangePersistenceService.persistExchange(
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
        conversationMemoryRefreshDispatcher.dispatchRefresh(execution.conversationId());
        return response;
    }

    public Flux<ChatStreamEventResponse> streamChat(Long sessionId, AppChatRequest request, AuthenticatedUser user) {
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
                    conversationMemoryRefreshDispatcher.dispatchRefresh(execution.conversationId());
                    return ChatStreamEventResponse.complete(response);
                }))
                .onErrorResume(ex -> Flux.just(ChatStreamEventResponse.error(resolveStreamErrorMessage(ex))));
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
        return "你是企业知识库问答助手。只能基于提供的知识片段回答，无法确认时要明确说明。";
    }

    private String buildGeneralSystemPrompt() {
        return "你是企业智能助手。优先给出直接、准确、可执行的回答；如果信息不充分，要明确说明你的判断边界。";
    }

    private String buildKnowledgeBaseUserPrompt(String question, String context, String webSearchContext) {
        StringBuilder prompt = new StringBuilder();
        if (StringUtils.hasText(context)) {
            prompt.append("知识片段：\n").append(context).append("\n\n");
        } else {
            prompt.append("当前没有命中知识片段。\n\n");
        }
        if (StringUtils.hasText(webSearchContext)) {
            prompt.append("联网搜索摘要：\n").append(webSearchContext).append("\n\n");
        }
        prompt.append("问题：\n").append(question).append("\n\n");

        if (StringUtils.hasText(context)) {
            if (StringUtils.hasText(webSearchContext)) {
                prompt.append("请优先基于知识片段回答，联网摘要仅作补充；若两者冲突，以知识片段为准。");
            } else {
                prompt.append("请基于知识片段回答，并尽量简洁。");
            }
            return prompt.toString();
        }

        if (StringUtils.hasText(webSearchContext)) {
            prompt.append("当前没有命中知识片段，你可以参考联网摘要回答，并明确说明这部分信息来自联网搜索结果。");
            return prompt.toString();
        }

        prompt.append("请明确说明无法从知识库确认答案。");
        return prompt.toString();
    }

    private String buildGeneralUserPrompt(String question, String webSearchContext) {
        if (!StringUtils.hasText(webSearchContext)) {
            return question;
        }
        return "联网搜索摘要：\n"
                + webSearchContext
                + "\n\n问题：\n"
                + question
                + "\n\n请优先结合联网摘要回答；如果摘要不足以支撑结论，要明确说明。";
    }

    private String buildWebSearchContext(List<WebSearchSnippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < snippets.size(); index++) {
            WebSearchSnippet snippet = snippets.get(index);
            if (snippet == null) {
                continue;
            }
            builder.append("结果").append(index + 1).append("：\n");
            if (StringUtils.hasText(snippet.title())) {
                builder.append("标题：").append(snippet.title()).append("\n");
            }
            if (StringUtils.hasText(snippet.snippet())) {
                builder.append("摘要：").append(snippet.snippet()).append("\n");
            }
            if (StringUtils.hasText(snippet.url())) {
                builder.append("链接：").append(snippet.url()).append("\n");
            }
            if (snippet.publishedAt() != null) {
                builder.append("时间：").append(snippet.publishedAt()).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

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

    private PreparedChatExecution prepareChatExecution(Long sessionId, AppChatRequest request, AuthenticatedUser user) {
        ChatSessionEntity session = requireAppSession(sessionId, user.getUserId());
        String sceneType = normalizeSceneType(session.getSceneType());

        List<Long> effectiveSelectedKbIds = request.getSelectedKbIds() == null
                ? loadSelectedKnowledgeBaseIds(session.getId()).getOrDefault(session.getId(), defaultSelectedKnowledgeBaseIds(session))
                : normalizeSelectedKnowledgeBaseIds(sceneType, session.getKbId(), request.getSelectedKbIds());
        if (request.getSelectedKbIds() != null) {
            replaceSessionKnowledgeBaseRelations(session.getId(), effectiveSelectedKbIds);
        }

        refreshSessionPreferences(session, request.getChatModelId(), request.getWebSearchEnabled());
        List<WebSearchSnippet> webSearchSnippets = loadWebSearchSnippets(request.getQuestion(), request.getWebSearchEnabled());

        RetrievalService.RetrievalResult retrievalResult = effectiveSelectedKbIds.isEmpty()
                ? new RetrievalService.RetrievalResult(List.of(), "")
                : retrievalService.retrieveAcrossKnowledgeBases(
                effectiveSelectedKbIds.stream().map(knowledgeBaseService::requireById).toList(),
                request.getQuestion()
        );

        ModelService.ChatModelDescriptor chatModel = resolveChatModel(session, effectiveSelectedKbIds, request.getChatModelId());
        List<ChatModelClient.ChatMessage> promptMessages = buildPromptMessages(
                sceneType,
                request.getQuestion(),
                retrievalResult,
                effectiveSelectedKbIds,
                webSearchSnippets
        );

        return new PreparedChatExecution(
                session,
                chatModel,
                retrievalResult,
                promptMessages,
                buildHistoryMessages(session.getId()),
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
            List<Long> selectedKbIds,
            Long requestChatModelId
    ) {
        if (requestChatModelId != null) {
            return modelService.resolveChatModelDescriptor(requestChatModelId);
        }
        if (session.getModelId() != null) {
            return modelService.resolveChatModelDescriptor(session.getModelId());
        }
        if (!selectedKbIds.isEmpty()) {
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(selectedKbIds.get(0));
            return modelService.resolveChatModelDescriptor(knowledgeBase.getChatModelId());
        }
        return modelService.resolveChatModelDescriptor(null);
    }

    private List<ChatModelClient.ChatMessage> buildPromptMessages(
            String sceneType,
            String question,
            RetrievalService.RetrievalResult retrievalResult,
            List<Long> selectedKbIds,
            List<WebSearchSnippet> webSearchSnippets
    ) {
        String webSearchContext = buildWebSearchContext(webSearchSnippets);
        if (!selectedKbIds.isEmpty() || ChatSceneTypes.KNOWLEDGE_BASE.equals(sceneType)) {
            return List.of(
                    new ChatModelClient.ChatMessage("system", buildKnowledgeBaseSystemPrompt()),
                    new ChatModelClient.ChatMessage("user", buildKnowledgeBaseUserPrompt(question, retrievalResult.context(), webSearchContext))
            );
        }
        return List.of(
                new ChatModelClient.ChatMessage("system", buildGeneralSystemPrompt()),
                new ChatModelClient.ChatMessage("user", buildGeneralUserPrompt(question, webSearchContext))
        );
    }

    private List<WebSearchSnippet> loadWebSearchSnippets(String question, Boolean webSearchEnabled) {
        if (!Boolean.TRUE.equals(webSearchEnabled) || !StringUtils.hasText(question)) {
            return List.of();
        }
        try {
            List<WebSearchSnippet> snippets = webSearchProvider.search(question, WEB_SEARCH_TOP_K);
            return snippets == null ? List.of() : snippets.stream().filter(Objects::nonNull).toList();
        } catch (Exception ex) {
            log.warn("联网搜索已降级为空结果，question={}", question, ex);
            return List.of();
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
