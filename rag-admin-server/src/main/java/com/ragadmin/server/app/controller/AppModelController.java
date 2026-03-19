package com.ragadmin.server.app.controller;

import com.ragadmin.server.app.service.AppPortalService;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.model.dto.ModelResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/models")
public class AppModelController {

    @Autowired
    private AppPortalService appPortalService;

    @GetMapping
    public ApiResponse<PageResponse<ModelResponse>> list(
            @RequestParam(required = false) String providerCode,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(appPortalService.listAvailableChatModels(providerCode, pageNo, pageSize));
    }
}
