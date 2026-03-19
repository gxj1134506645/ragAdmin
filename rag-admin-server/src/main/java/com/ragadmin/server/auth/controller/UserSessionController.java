package com.ragadmin.server.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ragadmin.server.auth.dto.KickoutUserSessionRequest;
import com.ragadmin.server.auth.dto.UserSessionDetailResponse;
import com.ragadmin.server.auth.dto.UserSessionListItemResponse;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.auth.service.UserSessionAdminService;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/user-sessions")
@SaCheckLogin(type = "admin")
@SaCheckPermission("USER_MANAGE")
public class UserSessionController {

    @Autowired
    private UserSessionAdminService userSessionAdminService;

    @GetMapping
    public ApiResponse<PageResponse<UserSessionListItemResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String onlineScope,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.success(userSessionAdminService.list(keyword, roleCode, onlineScope, pageNo, pageSize));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserSessionDetailResponse> detail(@PathVariable Long userId) {
        return ApiResponse.success(userSessionAdminService.detail(userId));
    }

    @PostMapping("/{userId}/kickout")
    public ApiResponse<Void> kickout(
            @PathVariable Long userId,
            @Valid @RequestBody KickoutUserSessionRequest request,
            HttpServletRequest httpServletRequest
    ) {
        userSessionAdminService.kickout(requireAuthenticatedUser(httpServletRequest), userId, request);
        return ApiResponse.success(null);
    }

    private AuthenticatedUser requireAuthenticatedUser(HttpServletRequest request) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) request.getAttribute(AuthService.REQUEST_ATTRIBUTE);
        if (authenticatedUser == null) {
            throw new BusinessException("UNAUTHORIZED", "当前登录态无效", HttpStatus.UNAUTHORIZED);
        }
        return authenticatedUser;
    }
}
