package com.ragadmin.server.document.dto;

import jakarta.validation.constraints.NotBlank;

public class InternalTaskCompleteRequest {

    @NotBlank(message = "taskStatus 不能为空")
    private String taskStatus;

    private String errorMessage;

    @NotBlank(message = "parseStatus 不能为空")
    private String parseStatus;

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }
}
