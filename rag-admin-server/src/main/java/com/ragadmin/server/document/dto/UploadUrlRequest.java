package com.ragadmin.server.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UploadUrlRequest {

    @NotBlank(message = "fileName 不能为空")
    private String fileName;

    @NotBlank(message = "contentType 不能为空")
    private String contentType;

    @NotBlank(message = "bizType 不能为空")
    private String bizType;
}
