package com.ragadmin.server.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateKnowledgeBaseRequest {

    @NotBlank(message = "kbCode 不能为空")
    private String kbCode;

    @NotBlank(message = "kbName 不能为空")
    private String kbName;

    private String description;

    @NotNull(message = "embeddingModelId 不能为空")
    private Long embeddingModelId;

    @NotNull(message = "retrieveTopK 不能为空")
    private Integer retrieveTopK;

    @NotNull(message = "rerankEnabled 不能为空")
    private Boolean rerankEnabled;

    private String retrievalMode;

    private String retrievalQueryRewritingMode;

    @NotBlank(message = "status 不能为空")
    private String status;
}
