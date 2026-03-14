package com.ragadmin.server.infra.ai.bailian;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.ragadmin.server.infra.ai.ProviderModelFactory;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "rag.ai.bailian", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BailianModelFactory implements ProviderModelFactory {

    private final BailianProperties bailianProperties;

    public BailianModelFactory(BailianProperties bailianProperties) {
        this.bailianProperties = bailianProperties;
    }

    @Override
    public boolean supports(String providerCode) {
        return "BAILIAN".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatModel createChatModel(String modelCode) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(SpringAiModelSupport.normalizeDashScopeBaseUrl(bailianProperties.getBaseUrl()))
                .apiKey(SpringAiModelSupport.requireApiKey(bailianProperties.getApiKey()))
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(bailianProperties.getTimeoutSeconds()))
                .build();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(modelCode)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(String modelCode) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .baseUrl(SpringAiModelSupport.normalizeDashScopeBaseUrl(bailianProperties.getBaseUrl()))
                .apiKey(SpringAiModelSupport.requireApiKey(bailianProperties.getApiKey()))
                .restClientBuilder(SpringAiModelSupport.createRestClientBuilder(bailianProperties.getTimeoutSeconds()))
                .build();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(modelCode)
                .build();
        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(dashScopeApi)
                .metadataMode(MetadataMode.NONE)
                .defaultOptions(options)
                .build();
    }
}
