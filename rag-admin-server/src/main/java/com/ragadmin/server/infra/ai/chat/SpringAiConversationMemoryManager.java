package com.ragadmin.server.infra.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.entity.ChatSessionMemorySummaryEntity;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMemorySummaryMapper;
import com.ragadmin.server.model.service.ModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class SpringAiConversationMemoryManager implements ConversationMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(SpringAiConversationMemoryManager.class);

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatSessionMemorySummaryMapper chatSessionMemorySummaryMapper;

    @Autowired
    private RedisShortTermChatMemoryStore redisShortTermChatMemoryStore;

    @Autowired
    private ConversationIdCodec conversationIdCodec;

    @Autowired
    private ChatMemoryProperties chatMemoryProperties;

    @Autowired
    private ConversationSummaryGenerator conversationSummaryGenerator;

    @Autowired
    private ConversationSummaryLockManager conversationSummaryLockManager;

    @Autowired
    private ModelService modelService;

    @Override
    public void clear(String conversationId) {
        chatMemory.clear(conversationId);
    }

    @Override
    public void refresh(String conversationId) {
        Long sessionId = conversationIdCodec.parseSessionId(conversationId);
        if (sessionId == null) {
            return;
        }

        Optional<ConversationSummaryLockManager.LockToken> lockToken = Optional.empty();
        try {
            lockToken = conversationSummaryLockManager.tryLock(conversationId);
            if (lockToken.isEmpty()) {
                log.debug("会话摘要刷新跳过，未获取到锁，conversationId={}", conversationId);
                return;
            }
            doRefresh(conversationId, sessionId);
        } catch (Exception ex) {
            // 记忆刷新属于附属链路，失败不能回滚主问答结果。
            log.warn("会话记忆刷新失败，conversationId={}, sessionId={}", conversationId, sessionId, ex);
        } finally {
            lockToken.ifPresent(token -> {
                if (!conversationSummaryLockManager.unlock(token)) {
                    log.debug("会话摘要刷新锁释放失败，conversationId={}", conversationId);
                }
            });
        }
    }

    private void doRefresh(String conversationId, Long sessionId) {
        List<ChatMessageEntity> exchanges = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getId));
        if (exchanges.isEmpty()) {
            redisShortTermChatMemoryStore.delete(conversationId);
            chatSessionMemorySummaryMapper.delete(new LambdaUpdateWrapper<ChatSessionMemorySummaryEntity>()
                    .eq(ChatSessionMemorySummaryEntity::getConversationId, conversationId));
            return;
        }

        List<ChatMessageEntity> olderExchanges = buildOlderExchanges(exchanges);
        String summaryText = buildSummaryText(conversationId, sessionId, olderExchanges);
        upsertSummary(sessionId, conversationId, summaryText, olderExchanges);
        redisShortTermChatMemoryStore.save(
                conversationId,
                redisShortTermChatMemoryStore.build(summaryText, buildRecentMessages(exchanges))
        );
    }

    private void upsertSummary(
            Long sessionId,
            String conversationId,
            String summaryText,
            List<ChatMessageEntity> olderExchanges
    ) {
        ChatSessionMemorySummaryEntity existing = chatSessionMemorySummaryMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionMemorySummaryEntity>()
                        .eq(ChatSessionMemorySummaryEntity::getConversationId, conversationId)
                        .last("LIMIT 1")
        );
        if (!StringUtils.hasText(summaryText)) {
            if (existing != null) {
                chatSessionMemorySummaryMapper.deleteById(existing.getId());
            }
            return;
        }

        ChatSessionMemorySummaryEntity entity = existing == null ? new ChatSessionMemorySummaryEntity() : existing;
        entity.setSessionId(sessionId);
        entity.setConversationId(conversationId);
        entity.setSummaryText(summaryText);
        entity.setCompressedMessageCount(olderExchanges.size());
        entity.setCompressedUntilMessageId(resolveCompressedUntilMessageId(olderExchanges));
        entity.setLastSourceMessageId(resolveLastSourceMessageId(olderExchanges));
        entity.setUpdatedAt(LocalDateTime.now());
        if (existing == null) {
            entity.setSummaryVersion(1);
            chatSessionMemorySummaryMapper.insert(entity);
            return;
        }
        entity.setSummaryVersion(Math.max(1, entity.getSummaryVersion()) + 1);
        chatSessionMemorySummaryMapper.updateById(entity);
    }

    private Long resolveCompressedUntilMessageId(List<ChatMessageEntity> olderExchanges) {
        if (olderExchanges.isEmpty()) {
            return null;
        }
        return olderExchanges.get(olderExchanges.size() - 1).getId();
    }

    private Long resolveLastSourceMessageId(List<ChatMessageEntity> olderExchanges) {
        if (olderExchanges.isEmpty()) {
            return null;
        }
        return olderExchanges.get(olderExchanges.size() - 1).getId();
    }

    private List<Message> buildRecentMessages(List<ChatMessageEntity> exchanges) {
        int keepRounds = Math.max(1, chatMemoryProperties.getShortTermRounds());
        List<ChatMessageEntity> recentExchanges = exchanges.size() <= keepRounds
                ? exchanges
                : exchanges.subList(exchanges.size() - keepRounds, exchanges.size());
        List<Message> messages = new ArrayList<>(recentExchanges.size() * 2);
        for (ChatMessageEntity exchange : recentExchanges) {
            if (StringUtils.hasText(exchange.getQuestionText())) {
                messages.add(new UserMessage(exchange.getQuestionText()));
            }
            if (StringUtils.hasText(exchange.getAnswerText())) {
                messages.add(new AssistantMessage(exchange.getAnswerText()));
            }
        }
        return messages;
    }

    private List<ChatMessageEntity> buildOlderExchanges(List<ChatMessageEntity> exchanges) {
        int keepRounds = Math.max(1, chatMemoryProperties.getShortTermRounds());
        if (exchanges.size() <= keepRounds) {
            return List.of();
        }
        return new ArrayList<>(exchanges.subList(0, exchanges.size() - keepRounds));
    }

    private String buildSummaryText(String conversationId, Long sessionId, List<ChatMessageEntity> olderExchanges) {
        if (olderExchanges.isEmpty()) {
            return null;
        }
        ModelService.ChatModelDescriptor chatModelDescriptor = resolveSummaryModel(sessionId, conversationId);
        ConversationSummaryResult result = conversationSummaryGenerator.generate(new ConversationSummaryRequest(
                conversationId,
                chatModelDescriptor == null ? null : chatModelDescriptor.providerCode(),
                chatModelDescriptor == null ? null : chatModelDescriptor.modelCode(),
                buildSummaryMessages(olderExchanges),
                chatMemoryProperties.getSummaryMaxLength()
        ));
        return result == null ? null : result.summaryText();
    }

    private ModelService.ChatModelDescriptor resolveSummaryModel(Long sessionId, String conversationId) {
        try {
            ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
            Long modelId = session == null ? null : session.getModelId();
            return modelService.resolveChatModelDescriptor(modelId);
        } catch (Exception ex) {
            log.warn("解析会话摘要模型失败，conversationId={}, sessionId={}", conversationId, sessionId, ex);
            return null;
        }
    }

    private List<ChatModelClient.ChatMessage> buildSummaryMessages(List<ChatMessageEntity> exchanges) {
        List<ChatModelClient.ChatMessage> messages = new ArrayList<>(exchanges.size() * 2);
        for (ChatMessageEntity exchange : exchanges) {
            if (StringUtils.hasText(exchange.getQuestionText())) {
                messages.add(new ChatModelClient.ChatMessage("user", exchange.getQuestionText()));
            }
            if (StringUtils.hasText(exchange.getAnswerText())) {
                messages.add(new ChatModelClient.ChatMessage("assistant", exchange.getAnswerText()));
            }
        }
        return messages;
    }
}
