package com.ragadmin.server.internal.service;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.internal.config.InternalAccessProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalAccessInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Internal-Token";

    @Resource
    private InternalAccessProperties internalAccessProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(token) || !token.equals(internalAccessProperties.getCallbackToken())) {
            throw new BusinessException("INTERNAL_UNAUTHORIZED", "内部回调鉴权失败", HttpStatus.UNAUTHORIZED);
        }
        return true;
    }
}
