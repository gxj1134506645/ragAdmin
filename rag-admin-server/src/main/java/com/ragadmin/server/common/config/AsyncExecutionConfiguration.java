package com.ragadmin.server.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncExecutionConfiguration implements AsyncConfigurer, SchedulingConfigurer {

    public static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";
    public static final String IO_VIRTUAL_TASK_EXECUTOR = "ioVirtualTaskExecutor";
    public static final String APPLICATION_TASK_SCHEDULER = "applicationTaskScheduler";

    private static final Logger log = LoggerFactory.getLogger(AsyncExecutionConfiguration.class);

    private final AsyncExecutionProperties asyncExecutionProperties;

    public AsyncExecutionConfiguration(AsyncExecutionProperties asyncExecutionProperties) {
        this.asyncExecutionProperties = asyncExecutionProperties;
    }

    /**
     * 默认 @Async 走普通业务线程池，适用于短任务、CPU 计算和需要明确队列背压的场景。
     */
    @Bean(name = APPLICATION_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        AsyncExecutionProperties.Application application = asyncExecutionProperties.getApplication();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, application.getCoreSize()));
        executor.setMaxPoolSize(Math.max(executor.getCorePoolSize(), application.getMaxSize()));
        executor.setQueueCapacity(Math.max(0, application.getQueueCapacity()));
        executor.setKeepAliveSeconds(Math.max(30, application.getKeepAliveSeconds()));
        executor.setThreadNamePrefix("rag-app-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 虚拟线程执行器仅用于明显的阻塞型 IO 任务；是否放量由业务侧显式并发上限控制。
     */
    @Bean(name = IO_VIRTUAL_TASK_EXECUTOR, destroyMethod = "close")
    public ExecutorService ioVirtualTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 定时任务调度器只负责轻量调度与分发，不承载长时间阻塞的业务执行。
     */
    @Bean(name = APPLICATION_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler applicationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, asyncExecutionProperties.getScheduler().getPoolSize()));
        scheduler.setThreadNamePrefix("rag-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncUncaughtExceptionHandler();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(applicationTaskScheduler());
    }

    private static class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("异步方法执行失败，method={}", method.toGenericString(), ex);
        }
    }
}
