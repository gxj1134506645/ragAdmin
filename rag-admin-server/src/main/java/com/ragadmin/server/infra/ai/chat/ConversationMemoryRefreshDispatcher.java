package com.ragadmin.server.infra.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

import static com.ragadmin.server.common.config.AsyncExecutionConfiguration.IO_VIRTUAL_TASK_EXECUTOR;

@Component
public class ConversationMemoryRefreshDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryRefreshDispatcher.class);

    private final ConversationMemoryManager conversationMemoryManager;
    private final ExecutorService ioVirtualTaskExecutor;
    private final Semaphore refreshConcurrencyLimiter;
    private final Set<String> inFlightConversationIds = ConcurrentHashMap.newKeySet();

    public ConversationMemoryRefreshDispatcher(
            ConversationMemoryManager conversationMemoryManager,
            @Qualifier(IO_VIRTUAL_TASK_EXECUTOR) ExecutorService ioVirtualTaskExecutor,
            ChatMemoryProperties chatMemoryProperties
    ) {
        this.conversationMemoryManager = conversationMemoryManager;
        this.ioVirtualTaskExecutor = ioVirtualTaskExecutor;
        this.refreshConcurrencyLimiter = new Semaphore(Math.max(1, chatMemoryProperties.getRefreshMaxConcurrency()));
    }

    /**
     * 会话记忆刷新属于阻塞型 IO 后台任务，主问答链路只负责分发，不同步等待结果。
     */
    public void dispatchRefresh(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        if (!inFlightConversationIds.add(conversationId)) {
            log.debug("会话记忆刷新跳过，原因=本地已在执行，conversationId={}", conversationId);
            return;
        }
        if (!refreshConcurrencyLimiter.tryAcquire()) {
            inFlightConversationIds.remove(conversationId);
            log.warn("会话记忆刷新跳过，原因=达到并发上限，conversationId={}", conversationId);
            return;
        }
        try {
            ioVirtualTaskExecutor.submit(() -> runRefresh(conversationId));
        } catch (RejectedExecutionException ex) {
            refreshConcurrencyLimiter.release();
            inFlightConversationIds.remove(conversationId);
            log.warn("会话记忆刷新分发失败，conversationId={}, reason={}", conversationId, ex.getMessage());
        }
    }

    private void runRefresh(String conversationId) {
        try {
            conversationMemoryManager.refresh(conversationId);
        } finally {
            refreshConcurrencyLimiter.release();
            inFlightConversationIds.remove(conversationId);
        }
    }
}