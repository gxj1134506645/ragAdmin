package com.ragadmin.server.auth.service;

import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("UNAUTHORIZED", "缺少有效的 Bearer Token", HttpStatus.UNAUTHORIZED);
        }
        String accessToken = authorization.substring(7);
        AuthenticatedUser authenticatedUser = authService.authenticateAccessToken(accessToken, resolveLoginType(request.getRequestURI()));
        request.setAttribute(AuthService.REQUEST_ATTRIBUTE, authenticatedUser);
        return true;
    }

    private String resolveLoginType(String requestUri) {
        if (requestUri.startsWith("/api/admin/")) {
            return AuthService.ADMIN_LOGIN_TYPE;
        }
        if (requestUri.startsWith("/api/app/")) {
            return AuthService.APP_LOGIN_TYPE;
        }
        throw new BusinessException("UNAUTHORIZED", "无法识别的认证域", HttpStatus.UNAUTHORIZED);
    }
}
