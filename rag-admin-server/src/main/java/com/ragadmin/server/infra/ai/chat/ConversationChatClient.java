package com.ragadmin.server.infra.ai.chat;

import reactor.core.publisher.Flux;

import java.util.List;

public interface ConversationChatClient {

    ChatCompletionResult chat(
            String providerCode,
            String modelCode,
            List<ChatPromptMessage> promptMessages
    );

    <T> T chatEntity(
            String providerCode,
            String modelCode,
            List<ChatPromptMessage> promptMessages,
            Class<T> responseType
    );

    ChatCompletionResult chat(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatPromptMessage> promptMessages,
            List<ChatPromptMessage> historyMessages
    );

    Flux<org.springframework.ai.chat.model.ChatResponse> stream(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatPromptMessage> promptMessages,
            List<ChatPromptMessage> historyMessages
    );
}
