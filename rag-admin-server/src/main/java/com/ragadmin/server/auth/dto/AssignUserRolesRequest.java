package com.ragadmin.server.auth.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class AssignUserRolesRequest {

    @NotEmpty(message = "roleCodes 不能为空")
    private List<String> roleCodes;

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes;
    }
}
