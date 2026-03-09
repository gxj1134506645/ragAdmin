package com.ragadmin.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {

    @NotBlank(message = "username 不能为空")
    private String username;

    @NotBlank(message = "password 不能为空")
    private String password;

    @NotBlank(message = "displayName 不能为空")
    private String displayName;

    private String email;
    private String mobile;

    @NotBlank(message = "status 不能为空")
    private String status;

    private List<String> roleCodes;
}
