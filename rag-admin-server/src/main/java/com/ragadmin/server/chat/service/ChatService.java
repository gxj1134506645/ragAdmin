package com.ragadmin.server.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.chat.dto.ChatRequest;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatSessionResponse;
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

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    public ChatSessionResponse createSession(CreateChatSessionRequest request, AuthenticatedUser user) {
        knowledgeBaseService.requireById(request.getKbId());
        ChatSessionEntity session = new ChatSessionEntity();
        session.setKbId(request.getKbId());
        session.setUserId(user.getUserId());
        session.setSessionName(request.getSessionName());
        session.setStatus("ENABLED");
        chatSessionMapper.insert(session);
        return toSessionResponse(session);
    }

    public PageResponse<ChatSessionResponse> listSessions(Long kbId, AuthenticatedUser user, long pageNo, long pageSize) {
        Page<ChatSessionEntity> page = chatSessionMapper.selectPage(Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getUserId, user.getUserId())
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
        ChatSessionEntity session = requireSession(sessionId, user.getUserId());
        if (!session.getKbId().equals(request.getKbId())) {
            throw new BusinessException("CHAT_KB_MISMATCH", "会话与知识库不匹配", HttpStatus.BAD_REQUEST);
        }

        KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(session.getKbId());
        RetrievalService.RetrievalResult retrievalResult = retrievalService.retrieve(knowledgeBase, request.getQuestion());
        var chatModel = modelService.resolveChatModelDescriptor(knowledgeBase.getChatModelId());
        List<ChatModelClient.ChatMessage> historyMessages = buildHistoryMessages(session.getId());

        Instant start = Instant.now();
        ChatModelClient.ChatCompletionResult completion = conversationChatClient.chat(
                chatModel.providerCode(),
                chatModel.modelCode(),
                buildConversationId(session),
                List.of(
                        new ChatModelClient.ChatMessage("system", buildSystemPrompt()),
                        new ChatModelClient.ChatMessage("user", buildUserPrompt(request.getQuestion(), retrievalResult.context()))
                ),
                historyMessages
        );
        int latencyMs = (int) Duration.between(start, Instant.now()).toMillis();

        ChatMessageEntity message = new ChatMessageEntity();
        message.setSessionId(session.getId());
        message.setUserId(user.getUserId());
        message.setMessageType("RAG");
        message.setQuestionText(request.getQuestion());
        message.setAnswerText(completion.content());
        message.setModelId(chatModel.modelId());
        message.setPromptTokens(completion.promptTokens());
        message.setCompletionTokens(completion.completionTokens());
        message.setLatencyMs(latencyMs);
        chatMessageMapper.insert(message);

        for (int i = 0; i < retrievalResult.chunks().size(); i++) {
            var chunk = retrievalResult.chunks().get(i);
            ChatAnswerReferenceEntity ref = new ChatAnswerReferenceEntity();
            ref.setMessageId(message.getId());
            ref.setChunkId(chunk.chunk().getId());
            ref.setScore(BigDecimal.valueOf(chunk.score()));
            ref.setRankNo(i + 1);
            chatAnswerReferenceMapper.insert(ref);
        }

        List<Long> documentIds = retrievalResult.chunks().stream()
                .map(item -> item.chunk().getDocumentId())
                .distinct()
                .toList();
        Map<Long, String> documentNameResolver = documentIds.isEmpty()
                ? Map.of()
                : documentMapper.selectBatchIds(documentIds)
                .stream()
                .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getDocName));

        List<ChatReferenceResponse> references = retrievalService.toReferenceResponses(
                retrievalResult.chunks(),
                documentId -> documentNameResolver.get(documentId)
        );

        return new ChatResponse(
                message.getId(),
                completion.content(),
                references,
                new ChatUsageResponse(completion.promptTokens(), completion.completionTokens())
        );
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
        return new ChatSessionResponse(session.getId(), session.getKbId(), session.getSessionName(), session.getStatus());
    }

    private String buildSystemPrompt() {
        return "你是企业知识库问答助手。只能基于提供的知识片段回答，无法确认时要明确说明。";
    }

    private String buildUserPrompt(String question, String context) {
        if (context == null || context.isBlank()) {
            return "问题：\n" + question + "\n\n当前没有命中知识片段，请明确说明无法从知识库确认答案。";
        }
        return "知识片段：\n" + context + "\n\n问题：\n" + question + "\n\n请基于知识片段回答，并尽量简洁。";
    }

    /**
     * 会话记忆需要显式区分场景和知识库，避免首页通用会话、不同知识库会话之间互相污染。
     */
    private String buildConversationId(ChatSessionEntity session) {
        return "chat-scene-kb-user-" + session.getUserId()
                + "-kb-" + session.getKbId()
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
