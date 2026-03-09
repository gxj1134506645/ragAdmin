package com.ragadmin.server.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserRequest {

    @NotBlank(message = "displayName 不能为空")
    private String displayName;

    private String email;
    private String mobile;

    @NotBlank(message = "status 不能为空")
    private String status;

    private String password;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
