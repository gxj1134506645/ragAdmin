package com.ragadmin.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "loginId 不能为空")
    private String loginId;

    @NotBlank(message = "password 不能为空")
    private String password;
}
