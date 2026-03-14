package com.ragadmin.server.infra.ai.embedding;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.ollama", name = "enabled", havingValue = "true")
public class OllamaEmbeddingClient implements EmbeddingModelClient {

    private final OllamaProperties ollamaProperties;

    public OllamaEmbeddingClient(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "OLLAMA".equalsIgnoreCase(providerCode);
    }

    @Override
    public List<List<Float>> embed(String modelCode, List<String> inputs) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(ollamaProperties.getTimeoutSeconds()))
                .build();
        OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
                .model(modelCode)
                .build();
        OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .observationRegistry(ObservationRegistry.NOOP)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(inputs, options));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new BusinessException("EMBEDDING_FAILED", "Ollama Embedding 返回为空", HttpStatus.BAD_GATEWAY);
        }
        return response.getResults().stream()
                .sorted(Comparator.comparingInt(Embedding::getIndex))
                .map(embedding -> SpringAiModelSupport.toFloatList(embedding.getOutput()))
                .toList();
    }
}
