package com.ragadmin.server.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.entity.ChatSessionKnowledgeBaseRelEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatSessionMapper;
import com.ragadmin.server.chat.mapper.ChatSessionKnowledgeBaseRelMapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.ChunkVectorRefEntity;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.mapper.DocumentVersionMapper;
import com.ragadmin.server.knowledge.dto.CreateKnowledgeBaseRequest;
import com.ragadmin.server.knowledge.dto.KnowledgeBaseResponse;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.mapper.KnowledgeBaseMapper;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.task.entity.TaskRetryRecordEntity;
import com.ragadmin.server.task.entity.TaskStepRecordEntity;
import com.ragadmin.server.task.mapper.TaskRetryRecordMapper;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
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
public class KnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private ModelService modelService;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentVersionMapper documentVersionMapper;

    @Autowired
    private DocumentParseTaskMapper documentParseTaskMapper;

    @Autowired
    private ChunkMapper chunkMapper;

    @Autowired
    private ChunkVectorRefMapper chunkVectorRefMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatSessionKnowledgeBaseRelMapper chatSessionKnowledgeBaseRelMapper;

    @Autowired
    private ChatAnswerReferenceMapper chatAnswerReferenceMapper;

    @Autowired
    private TaskStepRecordMapper taskStepRecordMapper;

    @Autowired
    private TaskRetryRecordMapper taskRetryRecordMapper;

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
            modelService.requireEmbeddingModelDescriptor(request.getEmbeddingModelId());
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

    public KnowledgeBaseResponse update(Long kbId, CreateKnowledgeBaseRequest request) {
        KnowledgeBaseEntity entity = requireById(kbId);
        KnowledgeBaseEntity existing = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getKbCode, request.getKbCode())
                .ne(KnowledgeBaseEntity::getId, kbId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("KB_CODE_EXISTS", "知识库编码已存在", HttpStatus.BAD_REQUEST);
        }

        if (request.getEmbeddingModelId() != null) {
            modelService.requireEmbeddingModelDescriptor(request.getEmbeddingModelId());
        }
        if (request.getChatModelId() != null) {
            modelService.requireModelWithCapability(request.getChatModelId(), "TEXT_GENERATION");
        }

        entity.setKbCode(request.getKbCode());
        entity.setKbName(request.getKbName());
        entity.setDescription(request.getDescription());
        entity.setEmbeddingModelId(request.getEmbeddingModelId());
        entity.setChatModelId(request.getChatModelId());
        entity.setRetrieveTopK(request.getRetrieveTopK());
        entity.setRerankEnabled(request.getRerankEnabled());
        entity.setStatus(request.getStatus());
        knowledgeBaseMapper.updateById(entity);
        return getDetail(kbId);
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

    @Transactional
    public void delete(Long kbId) {
        KnowledgeBaseEntity entity = requireById(kbId);
        Long sessionCount = chatSessionMapper.selectCount(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getKbId, kbId));
        if (sessionCount != null && sessionCount > 0) {
            throw new BusinessException("KB_IN_USE", "知识库 " + entity.getKbName() + " 已存在对话会话，不能删除", HttpStatus.BAD_REQUEST);
        }
        Long sessionRelCount = chatSessionKnowledgeBaseRelMapper.selectCount(new LambdaQueryWrapper<ChatSessionKnowledgeBaseRelEntity>()
                .eq(ChatSessionKnowledgeBaseRelEntity::getKbId, kbId));
        if (sessionRelCount != null && sessionRelCount > 0) {
            throw new BusinessException("KB_IN_USE", "知识库 " + entity.getKbName() + " 已被会话绑定使用，不能删除", HttpStatus.BAD_REQUEST);
        }

        List<Long> documentIds = documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()
                        .select(DocumentEntity::getId)
                        .eq(DocumentEntity::getKbId, kbId))
                .stream()
                .map(DocumentEntity::getId)
                .toList();
        List<Long> parseTaskIds = documentParseTaskMapper.selectList(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                        .select(DocumentParseTaskEntity::getId)
                        .eq(DocumentParseTaskEntity::getKbId, kbId))
                .stream()
                .map(DocumentParseTaskEntity::getId)
                .toList();
        List<Long> chunkIds = chunkMapper.selectList(new LambdaQueryWrapper<ChunkEntity>()
                        .select(ChunkEntity::getId)
                        .eq(ChunkEntity::getKbId, kbId))
                .stream()
                .map(ChunkEntity::getId)
                .toList();

        if (!parseTaskIds.isEmpty()) {
            taskStepRecordMapper.delete(new LambdaQueryWrapper<TaskStepRecordEntity>()
                    .in(TaskStepRecordEntity::getTaskId, parseTaskIds));
            taskRetryRecordMapper.delete(new LambdaQueryWrapper<TaskRetryRecordEntity>()
                    .in(TaskRetryRecordEntity::getTaskId, parseTaskIds));
        }

        if (!chunkIds.isEmpty()) {
            chatAnswerReferenceMapper.delete(new LambdaQueryWrapper<ChatAnswerReferenceEntity>()
                    .in(ChatAnswerReferenceEntity::getChunkId, chunkIds));
        }

        chunkVectorRefMapper.delete(new LambdaQueryWrapper<ChunkVectorRefEntity>()
                .eq(ChunkVectorRefEntity::getKbId, kbId));
        chunkMapper.delete(new LambdaQueryWrapper<ChunkEntity>()
                .eq(ChunkEntity::getKbId, kbId));
        documentParseTaskMapper.delete(new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(DocumentParseTaskEntity::getKbId, kbId));

        if (!documentIds.isEmpty()) {
            documentVersionMapper.delete(new LambdaQueryWrapper<DocumentVersionEntity>()
                    .in(DocumentVersionEntity::getDocumentId, documentIds));
            documentMapper.delete(new LambdaQueryWrapper<DocumentEntity>()
                    .in(DocumentEntity::getId, documentIds));
        }

        knowledgeBaseMapper.deleteById(kbId);
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
