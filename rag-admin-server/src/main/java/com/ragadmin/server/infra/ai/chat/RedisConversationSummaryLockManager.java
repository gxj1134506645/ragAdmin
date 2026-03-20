package com.ragadmin.server.infra.ai.chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisConversationSummaryLockManager implements ConversationSummaryLockManager {

    private static final String UNLOCK_SCRIPT_TEXT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(UNLOCK_SCRIPT_TEXT, Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ChatMemoryProperties chatMemoryProperties;

    @Override
    public Optional<LockToken> tryLock(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        String key = buildSummaryLockKey(conversationId);
        String ownerToken = UUID.randomUUID().toString();
        boolean locked = Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(
                        key,
                        ownerToken,
                        Duration.ofSeconds(Math.max(1, chatMemoryProperties.getSummaryLockSeconds()))
                )
        );
        if (!locked) {
            return Optional.empty();
        }
        return Optional.of(new LockToken(key, ownerToken));
    }

    @Override
    public boolean unlock(LockToken lockToken) {
        if (lockToken == null
                || !StringUtils.hasText(lockToken.key())
                || !StringUtils.hasText(lockToken.ownerToken())) {
            return false;
        }
        Long result = stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(lockToken.key()),
                lockToken.ownerToken()
        );
        return result != null && result > 0;
    }

    private String buildSummaryLockKey(String conversationId) {
        return chatMemoryProperties.getRedisKeyPrefix() + ":summary-lock:" + conversationId;
    }
}

