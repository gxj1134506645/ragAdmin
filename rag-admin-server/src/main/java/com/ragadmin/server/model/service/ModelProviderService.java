package com.ragadmin.server.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.model.dto.CreateModelProviderRequest;
import com.ragadmin.server.model.dto.ModelProviderCapabilityHealthResponse;
import com.ragadmin.server.model.dto.ModelProviderHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelProviderResponse;
import com.ragadmin.server.model.entity.AiModelCapabilityEntity;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import com.ragadmin.server.infra.ai.chat.ChatClientRegistry;
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ModelProviderService {

    @Autowired
    private AiProviderMapper aiProviderMapper;

    @Autowired
    private AiModelMapper aiModelMapper;

    @Autowired
    private AiModelCapabilityMapper aiModelCapabilityMapper;

    @Autowired
    private ChatClientRegistry chatClientRegistry;

    @Autowired
    private EmbeddingClientRegistry embeddingClientRegistry;

    public List<ModelProviderResponse> list() {
        return aiProviderMapper.selectList(new LambdaQueryWrapper<AiProviderEntity>()
                        .orderByAsc(AiProviderEntity::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ModelProviderResponse create(CreateModelProviderRequest request) {
        AiProviderEntity existing = aiProviderMapper.selectOne(new LambdaQueryWrapper<AiProviderEntity>()
                .eq(AiProviderEntity::getProviderCode, request.getProviderCode())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("PROVIDER_CODE_EXISTS", "模型提供方编码已存在", HttpStatus.BAD_REQUEST);
        }

        AiProviderEntity entity = new AiProviderEntity();
        entity.setProviderCode(request.getProviderCode());
        entity.setProviderName(request.getProviderName());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setApiKeySecretRef(request.getApiKeySecretRef());
        entity.setStatus(request.getStatus());
        aiProviderMapper.insert(entity);
        return toResponse(entity);
    }

    public AiProviderEntity requireProvider(Long providerId) {
        AiProviderEntity provider = aiProviderMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException("PROVIDER_NOT_FOUND", "模型提供方不存在", HttpStatus.NOT_FOUND);
        }
        return provider;
    }

    public ModelProviderHealthCheckResponse healthCheck(Long providerId) {
        AiProviderEntity provider = requireProvider(providerId);
        List<ModelProviderCapabilityHealthResponse> checks = new ArrayList<>();
        checks.add(checkChatCapability(provider));
        checks.add(checkEmbeddingCapability(provider));

        boolean hasDown = checks.stream().anyMatch(item -> "DOWN".equals(item.status()));
        boolean hasUp = checks.stream().anyMatch(item -> "UP".equals(item.status()));
        String status = hasDown ? "DOWN" : (hasUp ? "UP" : "UNKNOWN");
        String message = hasDown ? "提供方探活失败，请检查 capabilityChecks"
                : hasUp ? "提供方探活成功" : "提供方当前没有可探活的模型能力";

        return new ModelProviderHealthCheckResponse(
                provider.getId(),
                provider.getProviderCode(),
                provider.getProviderName(),
                status,
                message,
                checks
        );
    }

    private ModelProviderCapabilityHealthResponse checkChatCapability(AiProviderEntity provider) {
        AiModelEntity model = findEnabledModelByCapability(provider.getId(), "TEXT_GENERATION");
        if (model == null) {
            return new ModelProviderCapabilityHealthResponse("TEXT_GENERATION", null, null, "UNKNOWN", "当前提供方下未配置可用聊天模型");
        }
        try {
            ChatModelClient client = chatClientRegistry.getClient(provider.getProviderCode());
            client.chat(model.getModelCode(), List.of(new ChatModelClient.ChatMessage("user", "ping")));
            return new ModelProviderCapabilityHealthResponse("TEXT_GENERATION", model.getId(), model.getModelCode(), "UP", "聊天能力可用");
        } catch (Exception ex) {
            return new ModelProviderCapabilityHealthResponse("TEXT_GENERATION", model.getId(), model.getModelCode(), "DOWN", resolveMessage(ex, "聊天能力探活失败"));
        }
    }

    private ModelProviderCapabilityHealthResponse checkEmbeddingCapability(AiProviderEntity provider) {
        AiModelEntity model = findEnabledModelByCapability(provider.getId(), "EMBEDDING");
        if (model == null) {
            return new ModelProviderCapabilityHealthResponse("EMBEDDING", null, null, "UNKNOWN", "当前提供方下未配置可用向量模型");
        }
        try {
            embeddingClientRegistry.getClient(provider.getProviderCode())
                    .embed(model.getModelCode(), List.of("health check"));
            return new ModelProviderCapabilityHealthResponse("EMBEDDING", model.getId(), model.getModelCode(), "UP", "向量能力可用");
        } catch (Exception ex) {
            return new ModelProviderCapabilityHealthResponse("EMBEDDING", model.getId(), model.getModelCode(), "DOWN", resolveMessage(ex, "向量能力探活失败"));
        }
    }

    private AiModelEntity findEnabledModelByCapability(Long providerId, String capabilityType) {
        List<Long> modelIds = aiModelCapabilityMapper.selectModelIdsByCapabilityType(capabilityType);
        if (modelIds == null || modelIds.isEmpty()) {
            return null;
        }
        return aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
                .eq(AiModelEntity::getProviderId, providerId)
                .eq(AiModelEntity::getStatus, "ENABLED")
                .in(AiModelEntity::getId, modelIds)
                .orderByAsc(AiModelEntity::getId)
                .last("LIMIT 1"));
    }

    private String resolveMessage(Exception ex, String fallback) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }

    private ModelProviderResponse toResponse(AiProviderEntity entity) {
        return new ModelProviderResponse(
                entity.getId(),
                entity.getProviderCode(),
                entity.getProviderName(),
                entity.getBaseUrl(),
                entity.getApiKeySecretRef(),
                entity.getStatus()
        );
    }
}
