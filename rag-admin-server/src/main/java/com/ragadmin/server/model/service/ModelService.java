package com.ragadmin.server.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.entity.ChunkVectorRefEntity;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import com.ragadmin.server.infra.ai.AiProperties;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.mapper.KnowledgeBaseMapper;
import com.ragadmin.server.model.dto.CreateModelRequest;
import com.ragadmin.server.model.dto.ModelCapabilityHealthResponse;
import com.ragadmin.server.model.dto.ModelHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelResponse;
import com.ragadmin.server.model.dto.UpdateModelRequest;
import com.ragadmin.server.model.entity.AiModelCapabilityEntity;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    @Autowired
    private AiModelMapper aiModelMapper;

    @Autowired
    private AiProviderMapper aiProviderMapper;

    @Autowired
    private AiModelCapabilityMapper aiModelCapabilityMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private ChunkVectorRefMapper chunkVectorRefMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ModelProviderService modelProviderService;

    @Autowired
    private ConversationChatClient conversationChatClient;

    @Autowired
    private EmbeddingClientRegistry embeddingClientRegistry;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private BailianProperties bailianProperties;

    @Autowired
    private OllamaProperties ollamaProperties;

    public PageResponse<ModelResponse> list(String providerCode, String capabilityType, String status, long pageNo, long pageSize) {
        LambdaQueryWrapper<AiModelEntity> wrapper = new LambdaQueryWrapper<AiModelEntity>()
                .eq(StringUtils.hasText(status), AiModelEntity::getStatus, status)
                .orderByDesc(AiModelEntity::getIsDefaultChatModel)
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
        Long effectiveDefaultChatModelId = resolveEffectiveDefaultChatModelId();

        List<ModelResponse> list = records.stream()
                .map(model -> toResponse(
                        model,
                        providerMap.get(model.getProviderId()),
                        capabilityMap.getOrDefault(model.getId(), List.of()),
                        Objects.equals(model.getId(), effectiveDefaultChatModelId)
                ))
                .toList();

        return new PageResponse<>(list, pageNo, pageSize, page.getTotal());
    }

    @Transactional
    public ModelResponse create(CreateModelRequest request) {
        AiProviderEntity provider = modelProviderService.requireProvider(request.getProviderId());
        List<String> capabilityTypes = validateCapabilityTypes(request.getModelType(), request.getCapabilityTypes());
        ensureModelCodeUnique(null, request.getProviderId(), request.getModelCode());
        Integer normalizedMaxTokens = normalizeMaxTokens(request.getModelType(), request.getMaxTokens());
        BigDecimal normalizedTemperatureDefault = normalizeTemperatureDefault(request.getModelType(), request.getTemperatureDefault());

        AiModelEntity entity = new AiModelEntity();
        entity.setProviderId(request.getProviderId());
        entity.setModelCode(request.getModelCode());
        entity.setModelName(request.getModelName());
        entity.setModelType(request.getModelType());
        entity.setMaxTokens(normalizedMaxTokens);
        entity.setTemperatureDefault(normalizedTemperatureDefault);
        entity.setStatus(request.getStatus());
        entity.setIsDefaultChatModel(Boolean.FALSE);
        aiModelMapper.insert(entity);
        replaceCapabilities(entity.getId(), capabilityTypes);

        return toResponse(entity, provider, capabilityTypes, false);
    }

    @Transactional
    public ModelResponse update(Long modelId, UpdateModelRequest request) {
        AiModelEntity entity = requireModel(modelId);
        AiProviderEntity provider = modelProviderService.requireProvider(request.getProviderId());
        List<String> capabilityTypes = validateCapabilityTypes(request.getModelType(), request.getCapabilityTypes());
        ensureModelCodeUnique(modelId, request.getProviderId(), request.getModelCode());
        validateCapabilityReferenceChange(modelId, capabilityTypes);
        validateDefaultChatModelMutation(entity, request.getModelType(), request.getStatus(), capabilityTypes);
        Integer normalizedMaxTokens = normalizeMaxTokens(request.getModelType(), request.getMaxTokens());
        BigDecimal normalizedTemperatureDefault = normalizeTemperatureDefault(request.getModelType(), request.getTemperatureDefault());

        entity.setProviderId(request.getProviderId());
        entity.setModelCode(request.getModelCode());
        entity.setModelName(request.getModelName());
        entity.setModelType(request.getModelType());
        entity.setMaxTokens(normalizedMaxTokens);
        entity.setTemperatureDefault(normalizedTemperatureDefault);
        entity.setStatus(request.getStatus());
        aiModelMapper.updateById(entity);
        replaceCapabilities(modelId, capabilityTypes);

        return toResponse(entity, provider, capabilityTypes, Boolean.TRUE.equals(entity.getIsDefaultChatModel()));
    }

    @Transactional
    public ModelResponse setDefaultChatModel(Long modelId) {
        AiModelEntity model = requireModelWithCapability(modelId, "TEXT_GENERATION");
        if (!"ENABLED".equalsIgnoreCase(model.getStatus())) {
            throw new BusinessException("DEFAULT_CHAT_MODEL_INVALID", "默认聊天模型必须处于启用状态", HttpStatus.BAD_REQUEST);
        }

        AiModelEntity clearPatch = new AiModelEntity();
        clearPatch.setIsDefaultChatModel(Boolean.FALSE);
        aiModelMapper.update(
                clearPatch,
                new LambdaQueryWrapper<AiModelEntity>()
                        .eq(AiModelEntity::getIsDefaultChatModel, Boolean.TRUE)
                        .ne(AiModelEntity::getId, modelId)
        );

        if (!Boolean.TRUE.equals(model.getIsDefaultChatModel())) {
            model.setIsDefaultChatModel(Boolean.TRUE);
            aiModelMapper.updateById(model);
        }

        AiProviderEntity provider = requireProvider(model.getProviderId());
        List<String> capabilityTypes = aiModelCapabilityMapper.selectEnabledByModelIds(List.of(modelId))
                .stream()
                .map(AiModelCapabilityEntity::getCapabilityType)
                .distinct()
                .toList();
        return toResponse(model, provider, capabilityTypes, true);
    }

    @Transactional
    public void delete(Long modelId) {
        AiModelEntity model = requireModel(modelId);
        if (Boolean.TRUE.equals(model.getIsDefaultChatModel())) {
            throw new BusinessException("DEFAULT_CHAT_MODEL_DELETE_FORBIDDEN", "默认聊天模型不能直接删除，请先切换新的默认聊天模型", HttpStatus.BAD_REQUEST);
        }
        validateDeleteReference(modelId, model.getModelName());
        aiModelCapabilityMapper.delete(new LambdaQueryWrapper<AiModelCapabilityEntity>()
                .eq(AiModelCapabilityEntity::getModelId, modelId));
        aiModelMapper.deleteById(modelId);
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
        return toEmbeddingDescriptor(model, provider);
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
        log.info("开始模型探活，modelId={}, modelName={}, modelCode={}",
                modelId, model.getModelName(), model.getModelCode());
        List<String> capabilityTypes = aiModelCapabilityMapper.selectEnabledByModelIds(List.of(modelId))
                .stream()
                .map(AiModelCapabilityEntity::getCapabilityType)
                .distinct()
                .toList();
        if (capabilityTypes.isEmpty()) {
            log.warn("模型探活失败，modelId={}, modelName={}, modelCode={}, message={}",
                    modelId, model.getModelName(), model.getModelCode(), "模型未配置能力类型");
            throw new BusinessException("MODEL_CAPABILITY_EMPTY", "模型未配置能力类型", HttpStatus.BAD_REQUEST);
        }

        // 探活按能力逐项执行，返回结果里保留每一项状态，便于联调时直接定位失败点。
        List<ModelCapabilityHealthResponse> checks = capabilityTypes.stream()
                .map(capabilityType -> checkCapability(provider, model, capabilityType))
                .toList();
        boolean success = checks.stream().allMatch(item -> "UP".equals(item.status()));
        String message = success ? "模型探活成功" : "模型探活失败，请检查 capabilityChecks";
        ModelHealthCheckResponse response = new ModelHealthCheckResponse(
                model.getId(),
                model.getModelCode(),
                provider.getProviderCode(),
                success ? "UP" : "DOWN",
                message,
                checks
        );
        log.info("模型探活完成，modelId={}, modelName={}, modelCode={}, status={}, capabilityChecks={}",
                modelId, model.getModelName(), model.getModelCode(), response.status(), buildCapabilitySummary(checks));
        return response;
    }

    private void ensureModelCodeUnique(Long currentModelId, Long providerId, String modelCode) {
        AiModelEntity existing = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
                .eq(AiModelEntity::getProviderId, providerId)
                .eq(AiModelEntity::getModelCode, modelCode)
                .ne(currentModelId != null, AiModelEntity::getId, currentModelId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("MODEL_CODE_EXISTS", "模型编码已存在", HttpStatus.BAD_REQUEST);
        }
    }

    private void replaceCapabilities(Long modelId, List<String> capabilityTypes) {
        aiModelCapabilityMapper.delete(new LambdaQueryWrapper<AiModelCapabilityEntity>()
                .eq(AiModelCapabilityEntity::getModelId, modelId));
        for (String capabilityType : capabilityTypes) {
            AiModelCapabilityEntity capability = new AiModelCapabilityEntity();
            capability.setModelId(modelId);
            capability.setCapabilityType(capabilityType);
            capability.setEnabled(Boolean.TRUE);
            aiModelCapabilityMapper.insert(capability);
        }
    }

    private ModelCapabilityHealthResponse checkCapability(AiProviderEntity provider, AiModelEntity model, String capabilityType) {
        try {
            if ("TEXT_GENERATION".equals(capabilityType)) {
                conversationChatClient.chat(
                        provider.getProviderCode(),
                        model.getModelCode(),
                        List.of(new ChatPromptMessage("user", "ping"))
                );
                return new ModelCapabilityHealthResponse(capabilityType, "UP", "聊天能力可用");
            }
            if ("EMBEDDING".equals(capabilityType)) {
                String modelCode = resolveEmbeddingModelCode(provider, model);
                embeddingClientRegistry.getClient(provider.getProviderCode())
                        .embed(modelCode, List.of("health check"));
                return new ModelCapabilityHealthResponse(capabilityType, "UP", "向量能力可用");
            }
            return new ModelCapabilityHealthResponse(capabilityType, "DOWN", "当前未实现该能力探活");
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "探活失败" : ex.getMessage();
            return new ModelCapabilityHealthResponse(capabilityType, "DOWN", message);
        }
    }

    private String buildCapabilitySummary(List<ModelCapabilityHealthResponse> checks) {
        return checks.stream()
                .map(item -> item.capabilityType() + "=" + item.status())
                .collect(Collectors.joining(", "));
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

    private List<String> validateCapabilityTypes(String modelType, List<String> capabilityTypes) {
        if (!StringUtils.hasText(modelType)) {
            throw new BusinessException("MODEL_TYPE_INVALID", "模型类型不能为空", HttpStatus.BAD_REQUEST);
        }
        List<String> distinctCapabilityTypes = capabilityTypes == null ? List.of() : capabilityTypes.stream().distinct().toList();
        if (distinctCapabilityTypes.isEmpty()) {
            throw new BusinessException("MODEL_CAPABILITY_EMPTY", "模型未配置能力类型", HttpStatus.BAD_REQUEST);
        }
        if ("CHAT".equalsIgnoreCase(modelType)) {
            if (!distinctCapabilityTypes.equals(List.of("TEXT_GENERATION"))) {
                throw new BusinessException("MODEL_CAPABILITY_INVALID", "聊天模型只能配置文本生成能力", HttpStatus.BAD_REQUEST);
            }
            return distinctCapabilityTypes;
        }
        if ("EMBEDDING".equalsIgnoreCase(modelType)) {
            if (!distinctCapabilityTypes.equals(List.of("EMBEDDING"))) {
                throw new BusinessException("MODEL_CAPABILITY_INVALID", "向量模型只能配置向量生成能力", HttpStatus.BAD_REQUEST);
            }
            return distinctCapabilityTypes;
        }
        throw new BusinessException("MODEL_TYPE_INVALID", "当前仅支持 CHAT 或 EMBEDDING 模型类型", HttpStatus.BAD_REQUEST);
    }

    /**
     * 向量模型不参与文本生成，这里的生成参数需要在保存时统一收口为 null。
     */
    private Integer normalizeMaxTokens(String modelType, Integer maxTokens) {
        return "EMBEDDING".equalsIgnoreCase(modelType) ? null : maxTokens;
    }

    /**
     * 温度仅对聊天生成模型有意义，向量模型配置后也不会生效。
     */
    private BigDecimal normalizeTemperatureDefault(String modelType, BigDecimal temperatureDefault) {
        return "EMBEDDING".equalsIgnoreCase(modelType) ? null : temperatureDefault;
    }

    private void validateCapabilityReferenceChange(Long modelId, List<String> capabilityTypes) {
        boolean hasEmbeddingCapability = capabilityTypes.contains("EMBEDDING");
        boolean hasChatCapability = capabilityTypes.contains("TEXT_GENERATION");
        if (!hasEmbeddingCapability) {
            Long embeddingRefCount = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                    .eq(KnowledgeBaseEntity::getEmbeddingModelId, modelId));
            if (embeddingRefCount != null && embeddingRefCount > 0) {
                throw new BusinessException("MODEL_CAPABILITY_IN_USE", "当前模型已被知识库作为向量模型使用，不能移除向量能力", HttpStatus.BAD_REQUEST);
            }
            Long vectorRefCount = chunkVectorRefMapper.selectCount(new LambdaQueryWrapper<ChunkVectorRefEntity>()
                    .eq(ChunkVectorRefEntity::getEmbeddingModelId, modelId));
            if (vectorRefCount != null && vectorRefCount > 0) {
                throw new BusinessException("MODEL_CAPABILITY_IN_USE", "当前模型已存在向量索引引用，不能移除向量能力", HttpStatus.BAD_REQUEST);
            }
        }
        if (!hasChatCapability) {
            Long chatRefCount = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                    .eq(KnowledgeBaseEntity::getChatModelId, modelId));
            if (chatRefCount != null && chatRefCount > 0) {
                throw new BusinessException("MODEL_CAPABILITY_IN_USE", "当前模型已被知识库作为对话模型使用，不能移除文本生成能力", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void validateDefaultChatModelMutation(
            AiModelEntity entity,
            String targetModelType,
            String targetStatus,
            List<String> capabilityTypes
    ) {
        if (!Boolean.TRUE.equals(entity.getIsDefaultChatModel())) {
            return;
        }
        if (isEligibleAsDefaultChatModel(targetModelType, targetStatus, capabilityTypes)) {
            return;
        }
        throw new BusinessException(
                "DEFAULT_CHAT_MODEL_CHANGE_FORBIDDEN",
                "当前模型已是默认聊天模型，请先切换新的默认聊天模型后再修改状态或用途",
                HttpStatus.BAD_REQUEST
        );
    }

    private void validateDeleteReference(Long modelId, String modelName) {
        Long kbRefCount = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .and(wrapper -> wrapper.eq(KnowledgeBaseEntity::getEmbeddingModelId, modelId)
                        .or()
                        .eq(KnowledgeBaseEntity::getChatModelId, modelId)));
        if (kbRefCount != null && kbRefCount > 0) {
            throw new BusinessException("MODEL_IN_USE", "模型 " + modelName + " 已被知识库引用，不能删除", HttpStatus.BAD_REQUEST);
        }

        Long vectorRefCount = chunkVectorRefMapper.selectCount(new LambdaQueryWrapper<ChunkVectorRefEntity>()
                .eq(ChunkVectorRefEntity::getEmbeddingModelId, modelId));
        if (vectorRefCount != null && vectorRefCount > 0) {
            throw new BusinessException("MODEL_IN_USE", "模型 " + modelName + " 已存在向量索引引用，不能删除", HttpStatus.BAD_REQUEST);
        }

        Long messageRefCount = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getModelId, modelId));
        if (messageRefCount != null && messageRefCount > 0) {
            throw new BusinessException("MODEL_IN_USE", "模型 " + modelName + " 已存在历史对话记录引用，不能删除", HttpStatus.BAD_REQUEST);
        }
    }

    private EmbeddingModelDescriptor resolveDefaultEmbeddingModelDescriptor() {
        String providerCode = resolveDefaultProviderCode();
        if ("BAILIAN".equalsIgnoreCase(providerCode)) {
            return requireEmbeddingDescriptorByProviderAndCode(providerCode, bailianProperties.getDefaultEmbeddingModel());
        }
        if ("OLLAMA".equalsIgnoreCase(providerCode)) {
            return requireEmbeddingDescriptorByProviderAndCode(providerCode, ollamaProperties.getDefaultEmbeddingModel());
        }
        throw new BusinessException("DEFAULT_EMBEDDING_MODEL_UNSUPPORTED", "当前默认提供方未配置 Embedding 默认模型", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private ChatModelDescriptor resolveDefaultChatModelDescriptor() {
        AiModelEntity defaultModel = findStoredDefaultChatModel();
        if (defaultModel == null) {
            throw new BusinessException("DEFAULT_CHAT_MODEL_NOT_CONFIGURED", "请先在模型管理中设置默认聊天模型", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!"ENABLED".equalsIgnoreCase(defaultModel.getStatus())) {
            throw new BusinessException("DEFAULT_CHAT_MODEL_INVALID", "当前默认聊天模型已失效，请在模型管理中重新设置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            return requireChatModelDescriptor(defaultModel.getId());
        } catch (BusinessException ex) {
            log.warn("默认聊天模型解析失败，modelId={}, message={}", defaultModel.getId(), ex.getMessage());
            throw new BusinessException("DEFAULT_CHAT_MODEL_INVALID", "当前默认聊天模型已失效，请在模型管理中重新设置", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private Long resolveEffectiveDefaultChatModelId() {
        AiModelEntity storedDefault = findStoredDefaultChatModel();
        if (storedDefault == null) {
            return null;
        }
        if (!"ENABLED".equalsIgnoreCase(storedDefault.getStatus())) {
            log.warn("已忽略无效的后台默认聊天模型，modelId={}, message={}", storedDefault.getId(), "默认聊天模型未启用");
            return null;
        }

        try {
            requireModelWithCapability(storedDefault.getId(), "TEXT_GENERATION");
            return storedDefault.getId();
        } catch (BusinessException ex) {
            log.warn("已忽略无效的后台默认聊天模型，modelId={}, message={}", storedDefault.getId(), ex.getMessage());
            return null;
        }
    }

    private AiModelEntity findStoredDefaultChatModel() {
        List<AiModelEntity> candidates = aiModelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
                .eq(AiModelEntity::getIsDefaultChatModel, Boolean.TRUE)
                .orderByDesc(AiModelEntity::getUpdatedAt, AiModelEntity::getId)
                .last("LIMIT 1"));
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.getFirst();
    }

    private boolean isEligibleAsDefaultChatModel(String modelType, String status, List<String> capabilityTypes) {
        return "ENABLED".equalsIgnoreCase(status)
                && "CHAT".equalsIgnoreCase(modelType)
                && capabilityTypes.contains("TEXT_GENERATION");
    }

    private EmbeddingModelDescriptor requireEmbeddingDescriptorByProviderAndCode(String providerCode, String modelCode) {
        AiProviderEntity provider = requireProviderByCode(providerCode);
        AiModelEntity model = requireModelByProviderAndCapability(provider.getId(), modelCode, "EMBEDDING");
        return toEmbeddingDescriptor(model, provider);
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

    private EmbeddingModelDescriptor toEmbeddingDescriptor(AiModelEntity model, AiProviderEntity provider) {
        return new EmbeddingModelDescriptor(
                model.getId(),
                resolveEmbeddingModelCode(provider, model),
                provider.getProviderCode(),
                provider.getProviderName()
        );
    }

    private String resolveEmbeddingModelCode(AiProviderEntity provider, AiModelEntity model) {
        if ("BAILIAN".equalsIgnoreCase(provider.getProviderCode())) {
            return SpringAiModelSupport.requireSupportedDashScopeTextEmbeddingModel(model.getModelCode());
        }
        return model.getModelCode();
    }

    private ModelResponse toResponse(
            AiModelEntity entity,
            AiProviderEntity provider,
            List<String> capabilityTypes,
            boolean isDefaultChatModel
    ) {
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
                entity.getStatus(),
                isDefaultChatModel
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
