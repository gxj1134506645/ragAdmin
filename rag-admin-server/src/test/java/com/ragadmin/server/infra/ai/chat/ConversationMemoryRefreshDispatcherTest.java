package com.ragadmin.server.infra.ai.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryRefreshDispatcherTest {

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    @Mock
    private ExecutorService ioVirtualTaskExecutor;

    private ChatMemoryProperties chatMemoryProperties;

    @BeforeEach
    void setUp() {
        chatMemoryProperties = new ChatMemoryProperties();
        chatMemoryProperties.setRefreshMaxConcurrency(1);
    }

    @Test
    void shouldDispatchRefreshOnIoVirtualExecutor() {
        ConversationMemoryRefreshDispatcher dispatcher = new ConversationMemoryRefreshDispatcher(
                conversationMemoryManager,
                ioVirtualTaskExecutor,
                chatMemoryProperties
        );
        when(ioVirtualTaskExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return CompletableFuture.completedFuture(null);
        });

        dispatcher.dispatchRefresh("conv-1");

        verify(ioVirtualTaskExecutor).submit(any(Runnable.class));
        verify(conversationMemoryManager).refresh("conv-1");
    }

    @Test
    void shouldSkipDuplicateConversationWhileTaskInFlight() {
        ConversationMemoryRefreshDispatcher dispatcher = new ConversationMemoryRefreshDispatcher(
                conversationMemoryManager,
                ioVirtualTaskExecutor,
                chatMemoryProperties
        );
        when(ioVirtualTaskExecutor.submit(any(Runnable.class))).thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatchRefresh("conv-1");
        dispatcher.dispatchRefresh("conv-1");

        verify(ioVirtualTaskExecutor, times(1)).submit(any(Runnable.class));
        verify(conversationMemoryManager, never()).refresh(any());
    }

    @Test
    void shouldSkipWhenRefreshConcurrencyLimitReached() {
        ConversationMemoryRefreshDispatcher dispatcher = new ConversationMemoryRefreshDispatcher(
                conversationMemoryManager,
                ioVirtualTaskExecutor,
                chatMemoryProperties
        );
        when(ioVirtualTaskExecutor.submit(any(Runnable.class))).thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatchRefresh("conv-1");
        dispatcher.dispatchRefresh("conv-2");

        verify(ioVirtualTaskExecutor, times(1)).submit(any(Runnable.class));
        verify(conversationMemoryManager, never()).refresh(any());
    }

    @Test
    void shouldReleaseSlotWhenExecutorRejectedTask() {
        ConversationMemoryRefreshDispatcher dispatcher = new ConversationMemoryRefreshDispatcher(
                conversationMemoryManager,
                ioVirtualTaskExecutor,
                chatMemoryProperties
        );
        when(ioVirtualTaskExecutor.submit(any(Runnable.class)))
                .thenThrow(new RejectedExecutionException("rejected"))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return CompletableFuture.completedFuture(null);
                });

        dispatcher.dispatchRefresh("conv-1");
        dispatcher.dispatchRefresh("conv-2");

        verify(ioVirtualTaskExecutor, times(2)).submit(any(Runnable.class));
        verify(conversationMemoryManager).refresh(eq("conv-2"));
    }
}