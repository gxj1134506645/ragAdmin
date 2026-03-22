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

    private static final String SUMMARY_CONTEXT_PROMPT = """
            以下是此前已经压缩过的会话摘要，请保留其中关键事实、约束、待办和结论，
            再结合后续新增对话输出一份更新后的完整摘要：
            """;

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

        ChatSessionMemorySummaryEntity existing = loadExistingSummary(conversationId);
        List<ChatMessageEntity> incrementalExchanges = buildIncrementalCompressibleExchanges(exchanges, existing);
        String summaryText = existing == null ? null : existing.getSummaryText();

        if (!incrementalExchanges.isEmpty()) {
            String refreshedSummaryText = buildSummaryText(conversationId, sessionId, summaryText, incrementalExchanges);
            if (StringUtils.hasText(refreshedSummaryText)) {
                summaryText = refreshedSummaryText;
                upsertSummary(sessionId, conversationId, existing, summaryText, incrementalExchanges);
            } else {
                log.debug("会话摘要刷新未生成有效摘要，沿用已有摘要，conversationId={}, sessionId={}", conversationId, sessionId);
            }
        }

        redisShortTermChatMemoryStore.save(
                conversationId,
                redisShortTermChatMemoryStore.build(summaryText, buildRecentMessages(exchanges))
        );
    }

    private ChatSessionMemorySummaryEntity loadExistingSummary(String conversationId) {
        return chatSessionMemorySummaryMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionMemorySummaryEntity>()
                        .eq(ChatSessionMemorySummaryEntity::getConversationId, conversationId)
                        .last("LIMIT 1")
        );
    }

    private void upsertSummary(
            Long sessionId,
            String conversationId,
            ChatSessionMemorySummaryEntity existing,
            String summaryText,
            List<ChatMessageEntity> incrementalExchanges
    ) {
        if (!StringUtils.hasText(summaryText) || incrementalExchanges.isEmpty()) {
            return;
        }

        ChatSessionMemorySummaryEntity entity = existing == null ? new ChatSessionMemorySummaryEntity() : existing;
        entity.setSessionId(sessionId);
        entity.setConversationId(conversationId);
        entity.setSummaryText(summaryText);
        entity.setCompressedMessageCount(resolveCompressedMessageCount(existing, incrementalExchanges));
        entity.setCompressedUntilMessageId(resolveCompressedUntilMessageId(incrementalExchanges));
        entity.setLastSourceMessageId(resolveLastSourceMessageId(incrementalExchanges));
        entity.setUpdatedAt(LocalDateTime.now());
        if (existing == null) {
            entity.setSummaryVersion(1);
            chatSessionMemorySummaryMapper.insert(entity);
            return;
        }
        entity.setSummaryVersion(resolveNextSummaryVersion(existing));
        chatSessionMemorySummaryMapper.updateById(entity);
    }

    private int resolveCompressedMessageCount(
            ChatSessionMemorySummaryEntity existing,
            List<ChatMessageEntity> incrementalExchanges
    ) {
        int existingCount = existing == null || existing.getCompressedMessageCount() == null
                ? 0
                : Math.max(0, existing.getCompressedMessageCount());
        return existingCount + incrementalExchanges.size();
    }

    private int resolveNextSummaryVersion(ChatSessionMemorySummaryEntity existing) {
        int currentVersion = existing == null || existing.getSummaryVersion() == null
                ? 1
                : Math.max(1, existing.getSummaryVersion());
        return currentVersion + 1;
    }

    private Long resolveCompressedUntilMessageId(List<ChatMessageEntity> incrementalExchanges) {
        if (incrementalExchanges.isEmpty()) {
            return null;
        }
        return incrementalExchanges.get(incrementalExchanges.size() - 1).getId();
    }

    private Long resolveLastSourceMessageId(List<ChatMessageEntity> incrementalExchanges) {
        if (incrementalExchanges.isEmpty()) {
            return null;
        }
        return incrementalExchanges.get(incrementalExchanges.size() - 1).getId();
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

    private List<ChatMessageEntity> buildIncrementalCompressibleExchanges(
            List<ChatMessageEntity> exchanges,
            ChatSessionMemorySummaryEntity existing
    ) {
        List<ChatMessageEntity> compressibleExchanges = buildCompressibleExchanges(exchanges);
        if (compressibleExchanges.isEmpty()) {
            return List.of();
        }

        Long compressedUntilMessageId = existing == null ? null : existing.getCompressedUntilMessageId();
        if (compressedUntilMessageId == null) {
            return compressibleExchanges;
        }

        // 只压缩上次摘要游标之后、且已经落到冷窗口中的新增消息。
        return compressibleExchanges.stream()
                .filter(exchange -> exchange.getId() != null && exchange.getId() > compressedUntilMessageId)
                .toList();
    }

    private List<ChatMessageEntity> buildCompressibleExchanges(List<ChatMessageEntity> exchanges) {
        int keepRounds = Math.max(1, chatMemoryProperties.getShortTermRounds());
        if (exchanges.size() <= keepRounds) {
            return List.of();
        }
        return new ArrayList<>(exchanges.subList(0, exchanges.size() - keepRounds));
    }

    private String buildSummaryText(
            String conversationId,
            Long sessionId,
            String existingSummaryText,
            List<ChatMessageEntity> incrementalExchanges
    ) {
        if (incrementalExchanges.isEmpty()) {
            return null;
        }
        ModelService.ChatModelDescriptor chatModelDescriptor = resolveSummaryModel(sessionId, conversationId);
        ConversationSummaryResult result = conversationSummaryGenerator.generate(new ConversationSummaryRequest(
                conversationId,
                chatModelDescriptor == null ? null : chatModelDescriptor.providerCode(),
                chatModelDescriptor == null ? null : chatModelDescriptor.modelCode(),
                buildSummaryMessages(existingSummaryText, incrementalExchanges),
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

    private List<ChatPromptMessage> buildSummaryMessages(
            String existingSummaryText,
            List<ChatMessageEntity> exchanges
    ) {
        List<ChatPromptMessage> messages = new ArrayList<>(exchanges.size() * 2 + 1);
        if (StringUtils.hasText(existingSummaryText)) {
            messages.add(new ChatPromptMessage(
                    "system",
                    SUMMARY_CONTEXT_PROMPT + "\n" + existingSummaryText
            ));
        }
        for (ChatMessageEntity exchange : exchanges) {
            if (StringUtils.hasText(exchange.getQuestionText())) {
                messages.add(new ChatPromptMessage("user", exchange.getQuestionText()));
            }
            if (StringUtils.hasText(exchange.getAnswerText())) {
                messages.add(new ChatPromptMessage("assistant", exchange.getAnswerText()));
            }
        }
        return messages;
    }
}
