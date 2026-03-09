package com.ragadmin.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "displayName 不能为空")
    private String displayName;

    private String email;
    private String mobile;

    @NotBlank(message = "status 不能为空")
    private String status;

    private String password;
}
