package com.ragadmin.server.infra.ai.embedding;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.bailian.BailianApiSupport;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

@Component
public class BailianEmbeddingClient implements EmbeddingModelClient {

    private final RestClient restClient;

    public BailianEmbeddingClient(BailianProperties bailianProperties) {
        this.restClient = BailianApiSupport.buildRestClient(bailianProperties);
    }

    @Override
    public boolean supports(String providerCode) {
        return "BAILIAN".equalsIgnoreCase(providerCode);
    }

    @Override
    public List<List<Float>> embed(String modelCode, List<String> inputs) {
        BailianEmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .body(new BailianEmbeddingRequest(modelCode, inputs, "float"))
                .retrieve()
                .body(BailianEmbeddingResponse.class);
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new BusinessException("EMBEDDING_FAILED", "百炼 Embedding 返回为空", HttpStatus.BAD_GATEWAY);
        }
        return response.data().stream()
                .sorted(Comparator.comparingInt(BailianEmbeddingData::index))
                .map(BailianEmbeddingData::embedding)
                .toList();
    }

    private record BailianEmbeddingRequest(String model, List<String> input, String encoding_format) {
    }

    private record BailianEmbeddingResponse(List<BailianEmbeddingData> data) {
    }

    private record BailianEmbeddingData(int index, List<Float> embedding) {
    }
}
