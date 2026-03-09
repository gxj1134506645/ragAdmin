package com.ragadmin.server.audit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuditWebMvcConfiguration implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;

    public AuditWebMvcConfiguration(AuditLogInterceptor auditLogInterceptor) {
        this.auditLogInterceptor = auditLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLogInterceptor).addPathPatterns("/api/**");
    }
}
