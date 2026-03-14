package com.ragadmin.server.infra.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

public interface ProviderModelFactory {

    boolean supports(String providerCode);

    ChatModel createChatModel(String modelCode);

    EmbeddingModel createEmbeddingModel(String modelCode);
}
