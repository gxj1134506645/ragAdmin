package com.ragadmin.server.internal.config;

import com.ragadmin.server.internal.service.InternalAccessInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InternalWebMvcConfiguration implements WebMvcConfigurer {

    @Resource
    private InternalAccessInterceptor internalAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalAccessInterceptor)
                .addPathPatterns("/api/internal/**");
    }
}
