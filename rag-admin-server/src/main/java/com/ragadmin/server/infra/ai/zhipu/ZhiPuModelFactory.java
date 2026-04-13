package com.ragadmin.server.infra.ai.zhipu;

import com.ragadmin.server.infra.ai.ProviderModelFactory;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.zhipuai.api.ZhiPuApiConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "rag.ai.zhipu", name = "enabled", havingValue = "true")
public class ZhiPuModelFactory implements ProviderModelFactory {

    private final ZhiPuProperties zhiPuProperties;

    public ZhiPuModelFactory(ZhiPuProperties zhiPuProperties) {
        this.zhiPuProperties = zhiPuProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "ZHIPU".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatModel createChatModel(String modelCode) {
        ZhiPuAiApi api = buildApi();
        ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
                .model(modelCode)
                .temperature(0.7)
                .build();
        return new ZhiPuAiChatModel(api, options, RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
    }

    @Override
    public EmbeddingModel createEmbeddingModel(String modelCode) {
        ZhiPuAiApi api = buildApi();
        ZhiPuAiEmbeddingOptions options = ZhiPuAiEmbeddingOptions.builder()
                .model(modelCode)
                .build();
        return new ZhiPuAiEmbeddingModel(api, MetadataMode.NONE, options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
    }

    private ZhiPuAiApi buildApi() {
        String baseUrl = StringUtils.hasText(zhiPuProperties.getBaseUrl())
                ? zhiPuProperties.getBaseUrl()
                : ZhiPuApiConstants.DEFAULT_BASE_URL;
        return ZhiPuAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(SpringAiModelSupport.requireApiKey(zhiPuProperties.getApiKey()))
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(zhiPuProperties.getTimeoutSeconds()))
                .build();
    }
}
