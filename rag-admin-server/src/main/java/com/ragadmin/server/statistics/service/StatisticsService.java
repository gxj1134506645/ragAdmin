package com.ragadmin.server.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.statistics.dto.KnowledgeBaseChatStatisticsResponse;
import com.ragadmin.server.statistics.dto.ModelCallStatisticsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ModelService modelService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    public List<ModelCallStatisticsResponse> modelCalls() {
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .isNotNull(ChatMessageEntity::getModelId));
        if (messages.isEmpty()) {
            return List.of();
        }

        Map<Long, AiModelEntity> modelMap = modelService.findByIds(messages.stream()
                        .map(ChatMessageEntity::getModelId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(AiModelEntity::getId, model -> model));

        return messages.stream()
                .collect(Collectors.groupingBy(ChatMessageEntity::getModelId))
                .entrySet()
                .stream()
                .map(entry -> {
                    AiModelEntity model = modelMap.get(entry.getKey());
                    List<ChatMessageEntity> items = entry.getValue();
                    long callCount = items.size();
                    long totalPromptTokens = items.stream().map(ChatMessageEntity::getPromptTokens).filter(java.util.Objects::nonNull).mapToLong(Integer::longValue).sum();
                    long totalCompletionTokens = items.stream().map(ChatMessageEntity::getCompletionTokens).filter(java.util.Objects::nonNull).mapToLong(Integer::longValue).sum();
                    long averageLatency = Math.round(items.stream().map(ChatMessageEntity::getLatencyMs).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0D));
                    return new ModelCallStatisticsResponse(
                            entry.getKey(),
                            model == null ? null : model.getModelCode(),
                            model == null ? null : model.getModelName(),
                            callCount,
                            totalPromptTokens,
                            totalCompletionTokens,
                            averageLatency
                    );
                })
                .sorted(Comparator.comparing(ModelCallStatisticsResponse::callCount).reversed())
                .toList();
    }

    public KnowledgeBaseChatStatisticsResponse knowledgeBaseChat(Long kbId) {
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseService.requireById(kbId);
        List<ChatSessionEntity> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getKbId, knowledgeBase.getId()));
        if (sessions.isEmpty()) {
            return new KnowledgeBaseChatStatisticsResponse(kbId, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        List<Long> sessionIds = sessions.stream().map(ChatSessionEntity::getId).toList();
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .in(ChatMessageEntity::getSessionId, sessionIds));

        long totalPromptTokens = messages.stream().map(ChatMessageEntity::getPromptTokens).filter(java.util.Objects::nonNull).mapToLong(Integer::longValue).sum();
        long totalCompletionTokens = messages.stream().map(ChatMessageEntity::getCompletionTokens).filter(java.util.Objects::nonNull).mapToLong(Integer::longValue).sum();
        long averageLatency = Math.round(messages.stream().map(ChatMessageEntity::getLatencyMs).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0D));
        long distinctUserCount = sessions.stream().map(ChatSessionEntity::getUserId).distinct().count();

        return new KnowledgeBaseChatStatisticsResponse(
                kbId,
                (long) sessions.size(),
                (long) messages.size(),
                distinctUserCount,
                totalPromptTokens,
                totalCompletionTokens,
                averageLatency
        );
    }
}
