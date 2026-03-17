package com.ragadmin.server.model.controller;

import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.model.dto.CreateModelProviderRequest;
import com.ragadmin.server.model.dto.ModelProviderHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelProviderResponse;
import com.ragadmin.server.model.service.ModelProviderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model-providers")
public class ModelProviderController {

    private static final Logger log = LoggerFactory.getLogger(ModelProviderController.class);

    @Autowired
    private ModelProviderService modelProviderService;

    @GetMapping
    public ApiResponse<List<ModelProviderResponse>> list() {
        return ApiResponse.success(modelProviderService.list());
    }

    @PostMapping
    public ApiResponse<ModelProviderResponse> create(@Valid @RequestBody CreateModelProviderRequest request) {
        return ApiResponse.success(modelProviderService.create(request));
    }

    @PostMapping("/{providerId}/health-check")
    public ApiResponse<ModelProviderHealthCheckResponse> healthCheck(@PathVariable Long providerId) {
        ModelProviderResponse provider = modelProviderService.get(providerId);
        log.info("开始模型提供方探活，providerId={}, providerName={}, providerCode={}",
                providerId, provider.providerName(), provider.providerCode());
        try {
            ModelProviderHealthCheckResponse response = modelProviderService.healthCheck(providerId);
            log.info("模型提供方探活完成，providerId={}, providerName={}, providerCode={}, status={}",
                    providerId, provider.providerName(), provider.providerCode(), response.status());
            return ApiResponse.success(response);
        } catch (RuntimeException ex) {
            log.warn("模型提供方探活失败，providerId={}, providerName={}, providerCode={}, message={}",
                    providerId, provider.providerName(), provider.providerCode(), ex.getMessage());
            throw ex;
        }
    }
}
