package com.ragadmin.server.document.controller;

import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.document.dto.InternalTaskCompleteRequest;
import com.ragadmin.server.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/tasks")
public class InternalTaskController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/{taskId}/complete")
    public ApiResponse<Void> complete(
            @PathVariable Long taskId,
            @Valid @RequestBody InternalTaskCompleteRequest request
    ) {
        documentService.completeInternalTask(taskId, request);
        return ApiResponse.success(null);
    }
}
