package com.ragadmin.server.document.controller;

import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.document.dto.UploadUrlRequest;
import com.ragadmin.server.document.dto.UploadUrlResponse;
import com.ragadmin.server.document.service.FileUploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/files")
public class FileController {

    private final FileUploadService fileUploadService;

    public FileController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping("/upload-url")
    public ApiResponse<UploadUrlResponse> createUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return ApiResponse.success(fileUploadService.createUploadUrl(request));
    }
}
