package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.ollama", name = "enabled", havingValue = "true")
public class OllamaChatClient implements ChatModelClient {

    private final OllamaProperties ollamaProperties;

    public OllamaChatClient(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "OLLAMA".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatCompletionResult chat(String modelCode, List<ChatMessage> messages) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(ollamaProperties.getTimeoutSeconds()))
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(modelCode)
                .build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .observationRegistry(ObservationRegistry.NOOP)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
        Prompt prompt = new Prompt(SpringAiModelSupport.toSpringMessages(messages), options);
        return SpringAiModelSupport.toChatCompletionResult(chatModel.call(prompt));
    }
}
