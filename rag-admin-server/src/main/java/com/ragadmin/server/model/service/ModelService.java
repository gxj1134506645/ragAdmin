package com.ragadmin.server.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.model.dto.CreateModelRequest;
import com.ragadmin.server.model.dto.ModelResponse;
import com.ragadmin.server.model.entity.AiModelCapabilityEntity;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
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

    private final AiModelMapper aiModelMapper;
    private final AiProviderMapper aiProviderMapper;
    private final AiModelCapabilityMapper aiModelCapabilityMapper;
    private final ModelProviderService modelProviderService;

    public ModelService(
            AiModelMapper aiModelMapper,
            AiProviderMapper aiProviderMapper,
            AiModelCapabilityMapper aiModelCapabilityMapper,
            ModelProviderService modelProviderService
    ) {
        this.aiModelMapper = aiModelMapper;
        this.aiProviderMapper = aiProviderMapper;
        this.aiModelCapabilityMapper = aiModelCapabilityMapper;
        this.modelProviderService = modelProviderService;
    }

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
}
