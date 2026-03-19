package com.ragadmin.server.infra.ai.chat;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpringAiConversationMemoryManager implements ConversationMemoryManager {

    @Autowired
    private ChatMemory chatMemory;

    @Override
    public void clear(String conversationId) {
        chatMemory.clear(conversationId);
    }
}
