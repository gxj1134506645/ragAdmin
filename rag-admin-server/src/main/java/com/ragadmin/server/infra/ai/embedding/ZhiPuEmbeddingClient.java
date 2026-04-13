package com.ragadmin.server.infra.ai.embedding;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.AiProviderExceptionSupport;
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
@ConditionalOnProperty(prefix = "rag.ai.zhipu", name = "enabled", havingValue = "true")
public class ZhiPuEmbeddingClient implements EmbeddingModelClient {

    private final SpringAiModelFactory springAiModelFactory;

    public ZhiPuEmbeddingClient(SpringAiModelFactory springAiModelFactory) {
        this.springAiModelFactory = springAiModelFactory;
    }

    @Override
    public boolean supports(String providerCode) {
        return "ZHIPU".equalsIgnoreCase(providerCode);
    }

    @Override
    public List<List<Float>> embed(String modelCode, List<String> inputs) {
        try {
            EmbeddingResponse response = springAiModelFactory.createEmbeddingModel("ZHIPU", modelCode)
                    .call(new EmbeddingRequest(inputs, null));
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                throw new BusinessException("EMBEDDING_FAILED", "智谱 Embedding 返回为空", HttpStatus.BAD_GATEWAY);
            }
            return response.getResults().stream()
                    .sorted(Comparator.comparingInt(Embedding::getIndex))
                    .map(embedding -> SpringAiModelSupport.toFloatList(embedding.getOutput()))
                    .toList();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AiProviderExceptionSupport.toBusinessException(ex, "EMBEDDING_PROVIDER_UNAVAILABLE", "智谱向量检索模型");
        }
    }
}
