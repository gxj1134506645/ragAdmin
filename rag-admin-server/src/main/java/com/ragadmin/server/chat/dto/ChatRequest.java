package com.ragadmin.server.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "question 不能为空")
    private String question;

    @NotNull(message = "kbId 不能为空")
    private Long kbId;

    private Boolean stream = Boolean.FALSE;
}
