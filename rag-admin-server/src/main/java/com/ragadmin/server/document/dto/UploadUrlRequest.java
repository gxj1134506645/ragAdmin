package com.ragadmin.server.document.dto;

import jakarta.validation.constraints.NotBlank;

public class UploadUrlRequest {

    @NotBlank(message = "fileName 不能为空")
    private String fileName;

    @NotBlank(message = "contentType 不能为空")
    private String contentType;

    @NotBlank(message = "bizType 不能为空")
    private String bizType;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }
}
