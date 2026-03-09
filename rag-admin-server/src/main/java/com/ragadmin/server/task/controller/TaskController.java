package com.ragadmin.server.task.controller;

import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.task.dto.TaskDetailResponse;
import com.ragadmin.server.task.dto.TaskListItemResponse;
import com.ragadmin.server.task.service.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TaskListItemResponse>> list(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) Long bizId,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(taskService.list(taskType, taskStatus, bizId, pageNo, pageSize));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> detail(@PathVariable Long taskId) {
        return ApiResponse.success(taskService.detail(taskId));
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<TaskDetailResponse> retry(@PathVariable Long taskId) {
        return ApiResponse.success(taskService.retry(taskId));
    }
}
