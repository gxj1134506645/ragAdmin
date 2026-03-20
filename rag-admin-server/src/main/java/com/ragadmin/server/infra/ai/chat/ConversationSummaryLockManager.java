package com.ragadmin.server.infra.ai.chat;

import java.util.Optional;

/**
 * 会话摘要刷新锁管理器。
 */
public interface ConversationSummaryLockManager {

    /**
     * 尝试获取指定会话的摘要刷新锁。
     *
     * @param conversationId 会话标识
     * @return 获取成功返回锁令牌，失败返回空
     */
    Optional<LockToken> tryLock(String conversationId);

    /**
     * 释放锁令牌。
     *
     * @param lockToken 锁令牌
     * @return 是否释放成功
     */
    boolean unlock(LockToken lockToken);

    /**
     * 锁令牌，包含 Redis key 和所有者 token。
     */
    record LockToken(String key, String ownerToken) {
    }
}

