package com.ragadmin.server.app.controller;

import com.ragadmin.server.app.service.AppPortalService;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.knowledge.dto.KnowledgeBaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/knowledge-bases")
public class AppKnowledgeBaseController {

    @Autowired
    private AppPortalService appPortalService;

    @GetMapping
    public ApiResponse<PageResponse<KnowledgeBaseResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(appPortalService.listVisibleKnowledgeBases(keyword, pageNo, pageSize));
    }
}
