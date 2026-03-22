package com.ragadmin.server.infra.ai.chat;

import reactor.core.publisher.Flux;

import java.util.List;

public interface ConversationChatClient {

    <T> T chatEntity(
            String providerCode,
            String modelCode,
            List<ChatModelClient.ChatMessage> promptMessages,
            Class<T> responseType
    );

    ChatModelClient.ChatCompletionResult chat(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatModelClient.ChatMessage> promptMessages,
            List<ChatModelClient.ChatMessage> historyMessages
    );

    Flux<org.springframework.ai.chat.model.ChatResponse> stream(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatModelClient.ChatMessage> promptMessages,
            List<ChatModelClient.ChatMessage> historyMessages
    );
}
