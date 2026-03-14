package com.ragadmin.server.infra.ai;

import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiModelFactory {

    private final List<ProviderModelFactory> providerFactories;

    public SpringAiModelFactory(List<ProviderModelFactory> providerFactories) {
        this.providerFactories = providerFactories;
    }

    public ChatModel createChatModel(String providerCode, String modelCode) {
        return getFactory(providerCode).createChatModel(modelCode);
    }

    public EmbeddingModel createEmbeddingModel(String providerCode, String modelCode) {
        return getFactory(providerCode).createEmbeddingModel(modelCode);
    }

    private ProviderModelFactory getFactory(String providerCode) {
        return providerFactories.stream()
                .filter(factory -> factory.supports(providerCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "AI_PROVIDER_UNSUPPORTED",
                        "当前未实现该模型提供方的 Spring AI 工厂",
                        HttpStatus.BAD_REQUEST
                ));
    }
}
