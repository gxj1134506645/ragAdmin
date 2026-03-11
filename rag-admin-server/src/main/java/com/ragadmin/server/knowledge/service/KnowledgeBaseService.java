package com.ragadmin.server.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.knowledge.dto.CreateKnowledgeBaseRequest;
import com.ragadmin.server.knowledge.dto.KnowledgeBaseResponse;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.mapper.KnowledgeBaseMapper;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.service.ModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private ModelService modelService;

    public PageResponse<KnowledgeBaseResponse> list(String keyword, String status, long pageNo, long pageSize) {
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(StringUtils.hasText(status), KnowledgeBaseEntity::getStatus, status)
                .and(StringUtils.hasText(keyword), q -> q.like(KnowledgeBaseEntity::getKbCode, keyword)
                        .or()
                        .like(KnowledgeBaseEntity::getKbName, keyword))
                .orderByDesc(KnowledgeBaseEntity::getId);

        Page<KnowledgeBaseEntity> page = knowledgeBaseMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageResponse<>(Collections.emptyList(), pageNo, pageSize, page.getTotal());
        }

        Map<Long, AiModelEntity> modelMap = modelService.findByIds(page.getRecords().stream()
                        .flatMap(item -> java.util.stream.Stream.of(item.getEmbeddingModelId(), item.getChatModelId()))
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(AiModelEntity::getId, Function.identity()));

        return new PageResponse<>(
                page.getRecords().stream().map(item -> toResponse(item, modelMap)).toList(),
                pageNo,
                pageSize,
                page.getTotal()
        );
    }

    public KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request, Long operatorUserId) {
        KnowledgeBaseEntity existing = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getKbCode, request.getKbCode())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("KB_CODE_EXISTS", "知识库编码已存在", HttpStatus.BAD_REQUEST);
        }

        if (request.getEmbeddingModelId() != null) {
            modelService.requireModelWithCapability(request.getEmbeddingModelId(), "EMBEDDING");
        }
        if (request.getChatModelId() != null) {
            modelService.requireModelWithCapability(request.getChatModelId(), "TEXT_GENERATION");
        }

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setKbCode(request.getKbCode());
        entity.setKbName(request.getKbName());
        entity.setDescription(request.getDescription());
        entity.setEmbeddingModelId(request.getEmbeddingModelId());
        entity.setChatModelId(request.getChatModelId());
        entity.setRetrieveTopK(request.getRetrieveTopK());
        entity.setRerankEnabled(request.getRerankEnabled());
        entity.setStatus(request.getStatus());
        entity.setCreatedBy(operatorUserId);
        knowledgeBaseMapper.insert(entity);
        return getDetail(entity.getId());
    }

    public KnowledgeBaseEntity requireById(Long kbId) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null) {
            throw new BusinessException("KB_NOT_FOUND", "知识库不存在", HttpStatus.NOT_FOUND);
        }
        return entity;
    }

    public KnowledgeBaseResponse getDetail(Long kbId) {
        KnowledgeBaseEntity entity = requireById(kbId);
        Map<Long, AiModelEntity> modelMap = modelService.findByIds(
                        java.util.stream.Stream.of(entity.getEmbeddingModelId(), entity.getChatModelId()).toList())
                .stream()
                .collect(Collectors.toMap(AiModelEntity::getId, Function.identity()));
        return toResponse(entity, modelMap);
    }

    public void updateStatus(Long kbId, String status) {
        KnowledgeBaseEntity entity = requireById(kbId);
        // 状态切换当前只允许在 ENABLED / DISABLED 间流转，避免把任意字符串直接写进核心状态字段。
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException("KB_STATUS_INVALID", "知识库状态不合法", HttpStatus.BAD_REQUEST);
        }
        entity.setStatus(status);
        knowledgeBaseMapper.updateById(entity);
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBaseEntity entity, Map<Long, AiModelEntity> modelMap) {
        AiModelEntity embeddingModel = modelMap.get(entity.getEmbeddingModelId());
        AiModelEntity chatModel = modelMap.get(entity.getChatModelId());
        return new KnowledgeBaseResponse(
                entity.getId(),
                entity.getKbCode(),
                entity.getKbName(),
                entity.getDescription(),
                entity.getEmbeddingModelId(),
                embeddingModel == null ? null : embeddingModel.getModelName(),
                entity.getChatModelId(),
                chatModel == null ? null : chatModel.getModelName(),
                entity.getRetrieveTopK(),
                entity.getRerankEnabled(),
                entity.getStatus()
        );
    }
}
