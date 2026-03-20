package com.ragadmin.server.infra.ai.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConversationSummaryLockManagerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisConversationSummaryLockManager lockManager;

    @BeforeEach
    void setUp() {
        ChatMemoryProperties properties = new ChatMemoryProperties();
        properties.setRedisKeyPrefix("rag:chat:memory");
        properties.setSummaryLockSeconds(15);

        lockManager = new RedisConversationSummaryLockManager();
        ReflectionTestUtils.setField(lockManager, "chatMemoryProperties", properties);
        ReflectionTestUtils.setField(lockManager, "stringRedisTemplate", stringRedisTemplate);
    }

    @Test
    void shouldAcquireLockWhenKeyAbsent() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("rag:chat:memory:summary-lock:conv-1"), any(), eq(Duration.ofSeconds(15))))
                .thenReturn(true);

        Optional<ConversationSummaryLockManager.LockToken> token = lockManager.tryLock("conv-1");

        assertTrue(token.isPresent());
        assertEquals("rag:chat:memory:summary-lock:conv-1", token.get().key());
        assertTrue(token.get().ownerToken().length() > 10);
    }

    @Test
    void shouldReturnEmptyWhenKeyAlreadyLocked() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("rag:chat:memory:summary-lock:conv-1"), any(), eq(Duration.ofSeconds(15))))
                .thenReturn(false);

        Optional<ConversationSummaryLockManager.LockToken> token = lockManager.tryLock("conv-1");

        assertTrue(token.isEmpty());
    }

    @Test
    void shouldUnlockWhenOwnerMatches() {
        ConversationSummaryLockManager.LockToken token =
                new ConversationSummaryLockManager.LockToken("rag:chat:memory:summary-lock:conv-1", "owner-1");
        when(stringRedisTemplate.execute(any(), eq(List.of("rag:chat:memory:summary-lock:conv-1")), eq("owner-1")))
                .thenReturn(1L);

        boolean released = lockManager.unlock(token);

        assertTrue(released);
        verify(stringRedisTemplate).execute(any(), eq(List.of("rag:chat:memory:summary-lock:conv-1")), eq("owner-1"));
    }

    @Test
    void shouldReturnFalseWhenUnlockScriptDidNotDelete() {
        ConversationSummaryLockManager.LockToken token =
                new ConversationSummaryLockManager.LockToken("rag:chat:memory:summary-lock:conv-1", "owner-2");
        when(stringRedisTemplate.execute(any(), eq(List.of("rag:chat:memory:summary-lock:conv-1")), eq("owner-2")))
                .thenReturn(0L);

        boolean released = lockManager.unlock(token);

        assertFalse(released);
    }

    @Test
    void shouldReturnFalseWhenTokenInvalid() {
        assertFalse(lockManager.unlock(null));
        assertFalse(lockManager.unlock(new ConversationSummaryLockManager.LockToken("", "owner")));
        assertFalse(lockManager.unlock(new ConversationSummaryLockManager.LockToken("key", "")));
    }
}
