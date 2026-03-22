package com.ragadmin.server.web;

import com.ragadmin.server.common.config.AsyncExecutionConfiguration;
import com.ragadmin.server.common.config.AsyncExecutionProperties;
import com.ragadmin.server.common.config.WebMvcAsyncConfiguration;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

final class TestWebMvcContextSupport {

    private TestWebMvcContextSupport() {
    }

    static ManagedMockMvc create(
            List<Object> controllers,
            List<Object> controllerAdvice,
            List<HandlerInterceptor> interceptors
    ) {
        GenericWebApplicationContext context = new GenericWebApplicationContext();
        context.setServletContext(new MockServletContext());
        AnnotatedBeanDefinitionReader beanDefinitionReader = new AnnotatedBeanDefinitionReader(context);

        registerBeans(context, controllers, "controller");
        registerBeans(context, controllerAdvice, "controllerAdvice");

        beanDefinitionReader.register(DelegatingWebMvcConfiguration.class, AsyncExecutionConfiguration.class, WebMvcAsyncConfiguration.class);
        context.getBeanFactory().registerSingleton("asyncExecutionProperties", new AsyncExecutionProperties());
        context.getBeanFactory().registerSingleton("testWebMvcConfigurer", new TestWebMvcConfigurer(interceptors));

        context.refresh();

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        return new ManagedMockMvc(context, mockMvc);
    }

    private static void registerBeans(GenericWebApplicationContext context, List<Object> beans, String beanPrefix) {
        for (int i = 0; i < beans.size(); i++) {
            Object bean = beans.get(i);
            context.getBeanFactory().registerSingleton(beanPrefix + i, bean);
        }
    }

    record ManagedMockMvc(GenericWebApplicationContext context, MockMvc mockMvc) implements AutoCloseable {

        @Override
        public void close() {
            context.close();
        }
    }

    private static final class TestWebMvcConfigurer implements WebMvcConfigurer {

        private final List<HandlerInterceptor> interceptors;

        private TestWebMvcConfigurer(List<HandlerInterceptor> interceptors) {
            this.interceptors = interceptors;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            interceptors.forEach(registry::addInterceptor);
        }
    }
}
