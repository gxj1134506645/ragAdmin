package com.ragadmin.server.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateKnowledgeBaseStatusRequest {

    @NotBlank(message = "status 不能为空")
    private String status;
}
