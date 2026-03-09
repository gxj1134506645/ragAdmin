package com.ragadmin.server.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentParseTaskEntity;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.document.mapper.DocumentParseTaskMapper;
import com.ragadmin.server.document.service.DocumentService;
import com.ragadmin.server.task.dto.TaskDetailResponse;
import com.ragadmin.server.task.dto.TaskListItemResponse;
import com.ragadmin.server.task.dto.TaskRetryRecordResponse;
import com.ragadmin.server.task.dto.TaskStepResponse;
import com.ragadmin.server.task.entity.TaskRetryRecordEntity;
import com.ragadmin.server.task.entity.TaskStepRecordEntity;
import com.ragadmin.server.task.mapper.TaskRetryRecordMapper;
import com.ragadmin.server.task.mapper.TaskStepRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final String TASK_TYPE_DOCUMENT_PARSE = "DOCUMENT_PARSE";
    private static final String BIZ_TYPE_DOCUMENT = "DOCUMENT";

    @Autowired
    private DocumentParseTaskMapper documentParseTaskMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private TaskStepRecordMapper taskStepRecordMapper;

    @Autowired
    private TaskRetryRecordMapper taskRetryRecordMapper;

    public PageResponse<TaskListItemResponse> list(String taskType, String taskStatus, Long bizId, long pageNo, long pageSize) {
        validateTaskType(taskType);

        LambdaQueryWrapper<DocumentParseTaskEntity> wrapper = new LambdaQueryWrapper<DocumentParseTaskEntity>()
                .eq(StringUtils.hasText(taskStatus), DocumentParseTaskEntity::getTaskStatus, taskStatus)
                .eq(bizId != null, DocumentParseTaskEntity::getDocumentId, bizId)
                .orderByDesc(DocumentParseTaskEntity::getId);

        Page<DocumentParseTaskEntity> page = documentParseTaskMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageResponse<>(Collections.emptyList(), pageNo, pageSize, page.getTotal());
        }

        Map<Long, DocumentEntity> documentMap = documentMapper.selectBatchIds(page.getRecords().stream()
                        .map(DocumentParseTaskEntity::getDocumentId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(DocumentEntity::getId, Function.identity()));

        return new PageResponse<>(
                page.getRecords().stream()
                        .map(task -> toListItem(task, documentMap.get(task.getDocumentId())))
                        .toList(),
                pageNo,
                pageSize,
                page.getTotal()
        );
    }

    public TaskDetailResponse detail(Long taskId) {
        DocumentParseTaskEntity task = requireTask(taskId);
        DocumentEntity document = documentMapper.selectById(task.getDocumentId());
        return toDetail(task, document, listSteps(taskId), listRetryRecords(taskId));
    }

    public TaskDetailResponse retry(Long taskId) {
        DocumentParseTaskEntity task = requireTask(taskId);
        if ("WAITING".equals(task.getTaskStatus()) || "RUNNING".equals(task.getTaskStatus())) {
            throw new BusinessException("TASK_RETRY_NOT_ALLOWED", "任务进行中，不能重复重试", HttpStatus.BAD_REQUEST);
        }
        DocumentParseTaskEntity retriedTask = documentService.submitParseTask(task.getDocumentId(), task.getRetryCount() + 1);
        recordRetry(retriedTask, "手动重试", "SUBMITTED");
        DocumentEntity document = documentMapper.selectById(retriedTask.getDocumentId());
        return toDetail(retriedTask, document, listSteps(retriedTask.getId()), listRetryRecords(retriedTask.getId()));
    }

    private void validateTaskType(String taskType) {
        if (StringUtils.hasText(taskType) && !TASK_TYPE_DOCUMENT_PARSE.equals(taskType)) {
            throw new BusinessException("TASK_TYPE_UNSUPPORTED", "当前仅支持 DOCUMENT_PARSE 任务", HttpStatus.BAD_REQUEST);
        }
    }

    private DocumentParseTaskEntity requireTask(Long taskId) {
        DocumentParseTaskEntity task = documentParseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("TASK_NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private TaskListItemResponse toListItem(DocumentParseTaskEntity task, DocumentEntity document) {
        return new TaskListItemResponse(
                task.getId(),
                TASK_TYPE_DOCUMENT_PARSE,
                task.getTaskStatus(),
                BIZ_TYPE_DOCUMENT,
                task.getDocumentId(),
                task.getKbId(),
                task.getDocumentId(),
                document == null ? null : document.getDocName(),
                task.getRetryCount(),
                task.getErrorMessage(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private TaskDetailResponse toDetail(
            DocumentParseTaskEntity task,
            DocumentEntity document,
            List<TaskStepResponse> steps,
            List<TaskRetryRecordResponse> retryRecords
    ) {
        return new TaskDetailResponse(
                task.getId(),
                TASK_TYPE_DOCUMENT_PARSE,
                task.getTaskStatus(),
                BIZ_TYPE_DOCUMENT,
                task.getDocumentId(),
                task.getKbId(),
                task.getDocumentId(),
                task.getDocumentVersionId(),
                document == null ? null : document.getDocName(),
                document == null ? null : document.getParseStatus(),
                task.getRetryCount(),
                task.getErrorMessage(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                steps,
                retryRecords
        );
    }

    private List<TaskStepResponse> listSteps(Long taskId) {
        return taskStepRecordMapper.selectList(new LambdaQueryWrapper<TaskStepRecordEntity>()
                        .eq(TaskStepRecordEntity::getTaskId, taskId)
                        .orderByAsc(TaskStepRecordEntity::getId))
                .stream()
                .map(step -> new TaskStepResponse(
                        step.getStepCode(),
                        step.getStepName(),
                        step.getStepStatus(),
                        step.getErrorMessage(),
                        step.getStartedAt(),
                        step.getFinishedAt()
                ))
                .toList();
    }

    private List<TaskRetryRecordResponse> listRetryRecords(Long taskId) {
        return taskRetryRecordMapper.selectList(new LambdaQueryWrapper<TaskRetryRecordEntity>()
                        .eq(TaskRetryRecordEntity::getTaskId, taskId)
                        .orderByAsc(TaskRetryRecordEntity::getRetryNo)
                        .orderByAsc(TaskRetryRecordEntity::getId))
                .stream()
                .map(record -> new TaskRetryRecordResponse(
                        record.getRetryNo(),
                        record.getRetryReason(),
                        record.getRetryResult(),
                        record.getCreatedAt()
                ))
                .toList();
    }

    private void recordRetry(DocumentParseTaskEntity task, String retryReason, String retryResult) {
        TaskRetryRecordEntity record = new TaskRetryRecordEntity();
        record.setTaskId(task.getId());
        record.setRetryNo(task.getRetryCount());
        record.setRetryReason(retryReason);
        record.setRetryResult(retryResult);
        taskRetryRecordMapper.insert(record);
    }
}
