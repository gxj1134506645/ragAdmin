package com.ragadmin.server.auth.controller;

import com.ragadmin.server.auth.dto.AssignUserRolesRequest;
import com.ragadmin.server.auth.dto.CreateUserRequest;
import com.ragadmin.server.auth.dto.UpdateUserRequest;
import com.ragadmin.server.auth.dto.UserListItemResponse;
import com.ragadmin.server.auth.service.UserAdminService;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    @Autowired
    private UserAdminService userAdminService;

    @GetMapping
    public ApiResponse<PageResponse<UserListItemResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(userAdminService.list(keyword, status, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<UserListItemResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userAdminService.create(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserListItemResponse> update(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ApiResponse.success(userAdminService.update(userId, request));
    }

    @PutMapping("/{userId}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long userId, @Valid @RequestBody AssignUserRolesRequest request) {
        userAdminService.assignRoles(userId, request.getRoleCodes());
        return ApiResponse.success(null);
    }
}
