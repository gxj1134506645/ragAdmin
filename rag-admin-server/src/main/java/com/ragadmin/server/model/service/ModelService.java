package com.ragadmin.server.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import com.ragadmin.server.infra.ai.AiProperties;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import com.ragadmin.server.infra.ai.chat.ChatClientRegistry;
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.model.dto.ModelCapabilityHealthResponse;
import com.ragadmin.server.model.dto.ModelHealthCheckResponse;
import com.ragadmin.server.model.dto.CreateModelRequest;
import com.ragadmin.server.model.dto.ModelResponse;
import com.ragadmin.server.model.entity.AiModelCapabilityEntity;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelService {

    @Autowired
    private AiModelMapper aiModelMapper;

    @Autowired
    private AiProviderMapper aiProviderMapper;

    @Autowired
    private AiModelCapabilityMapper aiModelCapabilityMapper;

    @Autowired
    private ModelProviderService modelProviderService;

    @Autowired
    private ChatClientRegistry chatClientRegistry;

    @Autowired
    private EmbeddingClientRegistry embeddingClientRegistry;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private BailianProperties bailianProperties;

    public PageResponse<ModelResponse> list(String providerCode, String capabilityType, String status, long pageNo, long pageSize) {
        LambdaQueryWrapper<AiModelEntity> wrapper = new LambdaQueryWrapper<AiModelEntity>()
                .eq(StringUtils.hasText(status), AiModelEntity::getStatus, status)
                .orderByDesc(AiModelEntity::getId);

        if (StringUtils.hasText(providerCode)) {
            AiProviderEntity provider = aiProviderMapper.selectOne(new LambdaQueryWrapper<AiProviderEntity>()
                    .eq(AiProviderEntity::getProviderCode, providerCode)
                    .last("LIMIT 1"));
            if (provider == null) {
                return new PageResponse<>(Collections.emptyList(), pageNo, pageSize, 0);
            }
            wrapper.eq(AiModelEntity::getProviderId, provider.getId());
        }

        if (StringUtils.hasText(capabilityType)) {
            List<Long> modelIds = aiModelCapabilityMapper.selectModelIdsByCapabilityType(capabilityType);
            if (modelIds.isEmpty()) {
                return new PageResponse<>(Collections.emptyList(), pageNo, pageSize, 0);
            }
            wrapper.in(AiModelEntity::getId, modelIds);
        }

        Page<AiModelEntity> page = aiModelMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<AiModelEntity> records = page.getRecords();
        if (records.isEmpty()) {
            return new PageResponse<>(Collections.emptyList(), pageNo, pageSize, page.getTotal());
        }

        Map<Long, AiProviderEntity> providerMap = aiProviderMapper.selectBatchIds(records.stream()
                        .map(AiModelEntity::getProviderId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(AiProviderEntity::getId, Function.identity()));

        Map<Long, List<String>> capabilityMap = aiModelCapabilityMapper.selectEnabledByModelIds(records.stream()
                        .map(AiModelEntity::getId)
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(
                        AiModelCapabilityEntity::getModelId,
                        Collectors.mapping(AiModelCapabilityEntity::getCapabilityType, Collectors.toList())
                ));

        List<ModelResponse> list = records.stream()
                .map(model -> toResponse(model, providerMap.get(model.getProviderId()), capabilityMap.getOrDefault(model.getId(), List.of())))
                .toList();

        return new PageResponse<>(list, pageNo, pageSize, page.getTotal());
    }

    @Transactional
    public ModelResponse create(CreateModelRequest request) {
        AiProviderEntity provider = modelProviderService.requireProvider(request.getProviderId());
        AiModelEntity existing = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
                .eq(AiModelEntity::getProviderId, request.getProviderId())
                .eq(AiModelEntity::getModelCode, request.getModelCode())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("MODEL_CODE_EXISTS", "模型编码已存在", HttpStatus.BAD_REQUEST);
        }

        AiModelEntity entity = new AiModelEntity();
        entity.setProviderId(request.getProviderId());
        entity.setModelCode(request.getModelCode());
        entity.setModelName(request.getModelName());
        entity.setModelType(request.getModelType());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setTemperatureDefault(request.getTemperatureDefault());
        entity.setStatus(request.getStatus());
        aiModelMapper.insert(entity);

        for (String capabilityType : request.getCapabilityTypes().stream().distinct().toList()) {
            AiModelCapabilityEntity capability = new AiModelCapabilityEntity();
            capability.setModelId(entity.getId());
            capability.setCapabilityType(capabilityType);
            capability.setEnabled(Boolean.TRUE);
            aiModelCapabilityMapper.insert(capability);
        }

        return toResponse(entity, provider, request.getCapabilityTypes().stream().distinct().toList());
    }

    public AiModelEntity requireModelWithCapability(Long modelId, String capabilityType) {
        AiModelEntity model = aiModelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("MODEL_NOT_FOUND", "模型不存在", HttpStatus.NOT_FOUND);
        }
        List<String> capabilityTypes = aiModelCapabilityMapper.selectEnabledByModelIds(List.of(modelId))
                .stream()
                .map(AiModelCapabilityEntity::getCapabilityType)
                .toList();
        if (!capabilityTypes.contains(capabilityType)) {
            throw new BusinessException("MODEL_CAPABILITY_INVALID", "模型能力类型不匹配", HttpStatus.BAD_REQUEST);
        }
        return model;
    }

    public List<AiModelEntity> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return aiModelMapper.selectBatchIds(ids);
    }

    public EmbeddingModelDescriptor requireEmbeddingModelDescriptor(Long modelId) {
        AiModelEntity model = requireModelWithCapability(modelId, "EMBEDDING");
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new BusinessException("PROVIDER_NOT_FOUND", "模型提供方不存在", HttpStatus.NOT_FOUND);
        }
        return new EmbeddingModelDescriptor(
                model.getId(),
                model.getModelCode(),
                provider.getProviderCode(),
                provider.getProviderName()
        );
    }

    public EmbeddingModelDescriptor resolveEmbeddingModelDescriptor(Long modelId) {
        return modelId != null ? requireEmbeddingModelDescriptor(modelId) : resolveDefaultEmbeddingModelDescriptor();
    }

    public ChatModelDescriptor requireChatModelDescriptor(Long modelId) {
        AiModelEntity model = requireModelWithCapability(modelId, "TEXT_GENERATION");
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new BusinessException("PROVIDER_NOT_FOUND", "模型提供方不存在", HttpStatus.NOT_FOUND);
        }
        return new ChatModelDescriptor(
                model.getId(),
                model.getModelCode(),
                provider.getProviderCode(),
                provider.getProviderName()
        );
    }

    public ChatModelDescriptor resolveChatModelDescriptor(Long modelId) {
        return modelId != null ? requireChatModelDescriptor(modelId) : resolveDefaultChatModelDescriptor();
    }

    public ModelHealthCheckResponse healthCheck(Long modelId) {
        AiModelEntity model = requireModel(modelId);
        AiProviderEntity provider = requireProvider(model.getProviderId());
        List<String> capabilityTypes = aiModelCapabilityMapper.selectEnabledByModelIds(List.of(modelId))
                .stream()
                .map(AiModelCapabilityEntity::getCapabilityType)
                .distinct()
                .toList();
        if (capabilityTypes.isEmpty()) {
            throw new BusinessException("MODEL_CAPABILITY_EMPTY", "模型未配置能力类型", HttpStatus.BAD_REQUEST);
        }

        // 探活按能力逐项执行，返回结果里保留每一项状态，便于联调时直接定位失败点。
        List<ModelCapabilityHealthResponse> checks = capabilityTypes.stream()
                .map(capabilityType -> checkCapability(provider, model, capabilityType))
                .toList();
        boolean success = checks.stream().allMatch(item -> "UP".equals(item.status()));
        String message = success ? "模型探活成功" : "模型探活失败，请检查 capabilityChecks";
        return new ModelHealthCheckResponse(
                model.getId(),
                model.getModelCode(),
                provider.getProviderCode(),
                success ? "UP" : "DOWN",
                message,
                checks
        );
    }

    private ModelCapabilityHealthResponse checkCapability(AiProviderEntity provider, AiModelEntity model, String capabilityType) {
        try {
            if ("TEXT_GENERATION".equals(capabilityType)) {
                ChatModelClient client = chatClientRegistry.getClient(provider.getProviderCode());
                client.chat(model.getModelCode(), List.of(new ChatModelClient.ChatMessage("user", "ping")));
                return new ModelCapabilityHealthResponse(capabilityType, "UP", "聊天能力可用");
            }
            if ("EMBEDDING".equals(capabilityType)) {
                embeddingClientRegistry.getClient(provider.getProviderCode())
                        .embed(model.getModelCode(), List.of("health check"));
                return new ModelCapabilityHealthResponse(capabilityType, "UP", "向量能力可用");
            }
            return new ModelCapabilityHealthResponse(capabilityType, "DOWN", "当前未实现该能力探活");
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "探活失败" : ex.getMessage();
            return new ModelCapabilityHealthResponse(capabilityType, "DOWN", message);
        }
    }

    private AiModelEntity requireModel(Long modelId) {
        AiModelEntity model = aiModelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("MODEL_NOT_FOUND", "模型不存在", HttpStatus.NOT_FOUND);
        }
        return model;
    }

    private AiProviderEntity requireProvider(Long providerId) {
        AiProviderEntity provider = aiProviderMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException("PROVIDER_NOT_FOUND", "模型提供方不存在", HttpStatus.NOT_FOUND);
        }
        return provider;
    }

    private EmbeddingModelDescriptor resolveDefaultEmbeddingModelDescriptor() {
        String providerCode = resolveDefaultProviderCode();
        if ("BAILIAN".equalsIgnoreCase(providerCode)) {
            return requireEmbeddingDescriptorByProviderAndCode(providerCode, bailianProperties.getDefaultEmbeddingModel());
        }
        throw new BusinessException("DEFAULT_EMBEDDING_MODEL_UNSUPPORTED", "当前默认提供方未配置 Embedding 默认模型", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private ChatModelDescriptor resolveDefaultChatModelDescriptor() {
        String providerCode = resolveDefaultProviderCode();
        if ("BAILIAN".equalsIgnoreCase(providerCode)) {
            return requireChatDescriptorByProviderAndCode(providerCode, bailianProperties.getDefaultChatModel());
        }
        throw new BusinessException("DEFAULT_CHAT_MODEL_UNSUPPORTED", "当前默认提供方未配置聊天默认模型", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private EmbeddingModelDescriptor requireEmbeddingDescriptorByProviderAndCode(String providerCode, String modelCode) {
        AiProviderEntity provider = requireProviderByCode(providerCode);
        AiModelEntity model = requireModelByProviderAndCapability(provider.getId(), modelCode, "EMBEDDING");
        return new EmbeddingModelDescriptor(model.getId(), model.getModelCode(), provider.getProviderCode(), provider.getProviderName());
    }

    private ChatModelDescriptor requireChatDescriptorByProviderAndCode(String providerCode, String modelCode) {
        AiProviderEntity provider = requireProviderByCode(providerCode);
        AiModelEntity model = requireModelByProviderAndCapability(provider.getId(), modelCode, "TEXT_GENERATION");
        return new ChatModelDescriptor(model.getId(), model.getModelCode(), provider.getProviderCode(), provider.getProviderName());
    }

    private String resolveDefaultProviderCode() {
        if (!StringUtils.hasText(aiProperties.getDefaultProvider())) {
            throw new BusinessException("DEFAULT_PROVIDER_NOT_CONFIGURED", "未配置默认模型提供方", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return aiProperties.getDefaultProvider();
    }

    private AiProviderEntity requireProviderByCode(String providerCode) {
        AiProviderEntity provider = aiProviderMapper.selectOne(new LambdaQueryWrapper<AiProviderEntity>()
                .eq(AiProviderEntity::getProviderCode, providerCode)
                .last("LIMIT 1"));
        if (provider == null) {
            throw new BusinessException("PROVIDER_NOT_FOUND", "默认模型提供方不存在", HttpStatus.NOT_FOUND);
        }
        return provider;
    }

    private AiModelEntity requireModelByProviderAndCapability(Long providerId, String modelCode, String capabilityType) {
        if (!StringUtils.hasText(modelCode)) {
            throw new BusinessException("DEFAULT_MODEL_NOT_CONFIGURED", "未配置默认模型编码", HttpStatus.SERVICE_UNAVAILABLE);
        }
        AiModelEntity model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
                .eq(AiModelEntity::getProviderId, providerId)
                .eq(AiModelEntity::getModelCode, modelCode)
                .last("LIMIT 1"));
        if (model == null) {
            throw new BusinessException("MODEL_NOT_FOUND", "默认模型不存在，请先在后台模型管理中维护", HttpStatus.NOT_FOUND);
        }
        requireModelWithCapability(model.getId(), capabilityType);
        return model;
    }

    private ModelResponse toResponse(AiModelEntity entity, AiProviderEntity provider, List<String> capabilityTypes) {
        return new ModelResponse(
                entity.getId(),
                entity.getProviderId(),
                provider == null ? null : provider.getProviderCode(),
                provider == null ? null : provider.getProviderName(),
                entity.getModelCode(),
                entity.getModelName(),
                capabilityTypes,
                entity.getModelType(),
                entity.getMaxTokens(),
                entity.getTemperatureDefault(),
                entity.getStatus()
        );
    }

    public record ChatModelDescriptor(
            Long modelId,
            String modelCode,
            String providerCode,
            String providerName
    ) {
    }
}
