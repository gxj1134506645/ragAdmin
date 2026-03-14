package com.ragadmin.server.infra.ai.ollama;

import com.ragadmin.server.infra.ai.ProviderModelFactory;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "rag.ai.ollama", name = "enabled", havingValue = "true")
public class OllamaModelFactory implements ProviderModelFactory {

    private final OllamaProperties ollamaProperties;

    public OllamaModelFactory(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "OLLAMA".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatModel createChatModel(String modelCode) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(ollamaProperties.getTimeoutSeconds()))
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(modelCode)
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .observationRegistry(ObservationRegistry.NOOP)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(String modelCode) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(ollamaProperties.getTimeoutSeconds()))
                .build();
        OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
                .model(modelCode)
                .build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .observationRegistry(ObservationRegistry.NOOP)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }
}
