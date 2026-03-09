package com.ragadmin.server.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.model.dto.CreateModelProviderRequest;
import com.ragadmin.server.model.dto.ModelProviderResponse;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelProviderService {

    @Autowired
    private AiProviderMapper aiProviderMapper;

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
