package com.ragadmin.server.knowledge.controller;

import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.knowledge.dto.CreateKnowledgeBaseRequest;
import com.ragadmin.server.knowledge.dto.KnowledgeBaseResponse;
import com.ragadmin.server.knowledge.dto.UpdateKnowledgeBaseStatusRequest;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public ApiResponse<PageResponse<KnowledgeBaseResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(knowledgeBaseService.list(keyword, status, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(
            @Valid @RequestBody CreateKnowledgeBaseRequest request,
            HttpServletRequest httpServletRequest
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) httpServletRequest.getAttribute(AuthService.REQUEST_ATTRIBUTE);
        return ApiResponse.success(knowledgeBaseService.create(request, authenticatedUser.userId()));
    }

    @GetMapping("/{kbId}")
    public ApiResponse<KnowledgeBaseResponse> detail(@PathVariable Long kbId) {
        return ApiResponse.success(knowledgeBaseService.getDetail(kbId));
    }

    @PutMapping("/{kbId}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long kbId,
            @Valid @RequestBody UpdateKnowledgeBaseStatusRequest request
    ) {
        knowledgeBaseService.updateStatus(kbId, request.getStatus());
        return ApiResponse.success(null);
    }
}
