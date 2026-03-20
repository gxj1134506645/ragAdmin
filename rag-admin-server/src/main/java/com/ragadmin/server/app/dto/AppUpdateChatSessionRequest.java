package com.ragadmin.server.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppUpdateChatSessionRequest {

    @NotBlank(message = "sessionName 不能为空")
    private String sessionName;

    private Long chatModelId;

    @NotNull(message = "webSearchEnabled 不能为空")
    private Boolean webSearchEnabled;
}
