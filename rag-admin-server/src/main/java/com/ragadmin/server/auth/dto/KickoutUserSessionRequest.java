package com.ragadmin.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KickoutUserSessionRequest {

    @NotBlank(message = "scope 不能为空")
    private String scope;

    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 长度不能超过 200")
    private String reason;
}
