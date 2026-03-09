package com.ragadmin.server.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AssignUserRolesRequest {

    @NotEmpty(message = "roleCodes 不能为空")
    private List<String> roleCodes;
}
