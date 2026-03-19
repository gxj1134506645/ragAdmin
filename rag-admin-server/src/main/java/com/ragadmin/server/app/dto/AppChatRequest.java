package com.ragadmin.server.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AppChatRequest {

    @NotBlank(message = "question 不能为空")
    private String question;

    private Long chatModelId;

    private List<Long> selectedKbIds;

    private Boolean webSearchEnabled;
}
