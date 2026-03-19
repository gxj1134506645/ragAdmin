package com.ragadmin.server.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppUpdateChatSessionRequest {

    @NotBlank(message = "sessionName 不能为空")
    private String sessionName;
}
