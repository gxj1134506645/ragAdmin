package com.ragadmin.server.model.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.model.dto.ModelHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelResponse;
import com.ragadmin.server.model.dto.CreateModelRequest;
import com.ragadmin.server.model.dto.UpdateModelRequest;
import com.ragadmin.server.model.service.ModelService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/models")
@SaCheckLogin(type = "admin")
@SaCheckPermission("MODEL_MANAGE")
public class ModelController {

    @Autowired
    private ModelService modelService;

    @GetMapping
    public ApiResponse<PageResponse<ModelResponse>> list(
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String capabilityType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(modelService.list(providerCode, capabilityType, status, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<ModelResponse> create(@Valid @RequestBody CreateModelRequest request) {
        return ApiResponse.success(modelService.create(request));
    }

    @PutMapping("/{modelId}")
    public ApiResponse<ModelResponse> update(@PathVariable Long modelId, @Valid @RequestBody UpdateModelRequest request) {
        return ApiResponse.success(modelService.update(modelId, request));
    }

    @PostMapping("/{modelId}/default-chat-model")
    public ApiResponse<ModelResponse> setDefaultChatModel(@PathVariable Long modelId) {
        return ApiResponse.success(modelService.setDefaultChatModel(modelId));
    }

    @DeleteMapping("/{modelId}")
    public ApiResponse<Void> delete(@PathVariable Long modelId) {
        modelService.delete(modelId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{modelId}/health-check")
    public ApiResponse<ModelHealthCheckResponse> healthCheck(@PathVariable Long modelId) {
        return ApiResponse.success(modelService.healthCheck(modelId));
    }
}
