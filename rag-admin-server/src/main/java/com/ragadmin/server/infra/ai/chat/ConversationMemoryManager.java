package com.ragadmin.server.infra.ai.chat;

public interface ConversationMemoryManager {

    /**
     * 按业务 conversationId 清理持久化会话记忆，避免删除会话后旧上下文残留。
     */
    void clear(String conversationId);
}
