package com.ragadmin.server.infra.ai.embedding;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelFactory;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.ollama", name = "enabled", havingValue = "true")
public class OllamaEmbeddingClient implements EmbeddingModelClient {

    private final SpringAiModelFactory springAiModelFactory;

    public OllamaEmbeddingClient(SpringAiModelFactory springAiModelFactory) {
        this.springAiModelFactory = springAiModelFactory;
    }

    @Override
    public boolean supports(String providerCode) {
        return "OLLAMA".equalsIgnoreCase(providerCode);
    }

    @Override
    public List<List<Float>> embed(String modelCode, List<String> inputs) {
        EmbeddingResponse response = springAiModelFactory.createEmbeddingModel("OLLAMA", modelCode)
                .call(new EmbeddingRequest(inputs, null));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new BusinessException("EMBEDDING_FAILED", "Ollama Embedding 返回为空", HttpStatus.BAD_GATEWAY);
        }
        return response.getResults().stream()
                .sorted(Comparator.comparingInt(Embedding::getIndex))
                .map(embedding -> SpringAiModelSupport.toFloatList(embedding.getOutput()))
                .toList();
    }
}
