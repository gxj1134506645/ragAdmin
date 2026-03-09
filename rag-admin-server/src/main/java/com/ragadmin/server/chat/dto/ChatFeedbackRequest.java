package com.ragadmin.server.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatFeedbackRequest {

    @NotBlank(message = "feedbackType 不能为空")
    private String feedbackType;

    private String comment;
}
