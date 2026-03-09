package com.ragadmin.server.auth.service;

import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("UNAUTHORIZED", "缺少有效的 Bearer Token", HttpStatus.UNAUTHORIZED);
        }
        String accessToken = authorization.substring(7);
        AuthenticatedUser authenticatedUser = authService.authenticateAccessToken(accessToken);
        request.setAttribute(AuthService.REQUEST_ATTRIBUTE, authenticatedUser);
        return true;
    }

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }
}
