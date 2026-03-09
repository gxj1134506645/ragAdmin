package com.ragadmin.server.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateKnowledgeBaseStatusRequest {

    @NotBlank(message = "status 不能为空")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
