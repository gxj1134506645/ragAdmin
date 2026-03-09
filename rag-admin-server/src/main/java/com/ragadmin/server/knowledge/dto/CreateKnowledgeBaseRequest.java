package com.ragadmin.server.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateKnowledgeBaseRequest {

    @NotBlank(message = "kbCode 不能为空")
    private String kbCode;

    @NotBlank(message = "kbName 不能为空")
    private String kbName;

    private String description;

    @NotNull(message = "embeddingModelId 不能为空")
    private Long embeddingModelId;

    @NotNull(message = "chatModelId 不能为空")
    private Long chatModelId;

    @NotNull(message = "retrieveTopK 不能为空")
    private Integer retrieveTopK;

    @NotNull(message = "rerankEnabled 不能为空")
    private Boolean rerankEnabled;

    @NotBlank(message = "status 不能为空")
    private String status;

    public String getKbCode() {
        return kbCode;
    }

    public void setKbCode(String kbCode) {
        this.kbCode = kbCode;
    }

    public String getKbName() {
        return kbName;
    }

    public void setKbName(String kbName) {
        this.kbName = kbName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(Long embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public Long getChatModelId() {
        return chatModelId;
    }

    public void setChatModelId(Long chatModelId) {
        this.chatModelId = chatModelId;
    }

    public Integer getRetrieveTopK() {
        return retrieveTopK;
    }

    public void setRetrieveTopK(Integer retrieveTopK) {
        this.retrieveTopK = retrieveTopK;
    }

    public Boolean getRerankEnabled() {
        return rerankEnabled;
    }

    public void setRerankEnabled(Boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
