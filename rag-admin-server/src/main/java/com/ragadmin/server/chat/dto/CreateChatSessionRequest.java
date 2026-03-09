package com.ragadmin.server.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateChatSessionRequest {

    @NotNull(message = "kbId 不能为空")
    private Long kbId;

    @NotBlank(message = "sessionName 不能为空")
    private String sessionName;
}
