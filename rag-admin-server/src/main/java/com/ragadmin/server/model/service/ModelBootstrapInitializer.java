package com.ragadmin.server.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import com.ragadmin.server.model.entity.AiModelCapabilityEntity;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ModelBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelBootstrapInitializer.class);

    private final AiProviderMapper aiProviderMapper;
    private final AiModelMapper aiModelMapper;
    private final AiModelCapabilityMapper aiModelCapabilityMapper;
    private final BailianProperties bailianProperties;
    private final OllamaProperties ollamaProperties;

    public ModelBootstrapInitializer(
            AiProviderMapper aiProviderMapper,
            AiModelMapper aiModelMapper,
            AiModelCapabilityMapper aiModelCapabilityMapper,
            BailianProperties bailianProperties,
            OllamaProperties ollamaProperties
    ) {
        this.aiProviderMapper = aiProviderMapper;
        this.aiModelMapper = aiModelMapper;
        this.aiModelCapabilityMapper = aiModelCapabilityMapper;
        this.bailianProperties = bailianProperties;
        this.ollamaProperties = ollamaProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bailianProperties.isEnabled()) {
            AiProviderEntity bailian = ensureProvider("BAILIAN", "阿里百炼", "https://dashscope.aliyuncs.com");
            ensureModel(bailian, "qwen-max", "通义千问 Max", "CHAT", List.of("TEXT_GENERATION"), 8000, new BigDecimal("0.7"));
            ensureModel(bailian, "text-embedding-v3", "通义文本向量", "EMBEDDING", List.of("EMBEDDING"), null, null);
        }

        if (ollamaProperties.isEnabled()) {
            AiProviderEntity ollama = ensureProvider("OLLAMA", "Ollama", "http://127.0.0.1:11434");
            // 启动初始化优先使用本地配置声明的默认模型，避免每次切换 Ollama 模型都需要改代码。
            ensureModel(
                    ollama,
                    ollamaProperties.getDefaultChatModel(),
                    "Ollama Chat Model",
                    "CHAT",
                    List.of("TEXT_GENERATION"),
                    4096,
                    new BigDecimal("0.7")
            );
            ensureModel(
                    ollama,
                    ollamaProperties.getDefaultEmbeddingModel(),
                    "Ollama Embedding Model",
                    "EMBEDDING",
                    List.of("EMBEDDING"),
                    null,
                    null
            );
        }

        log.info("已完成默认模型提供方与模型定义初始化");
    }

    private AiProviderEntity ensureProvider(String code, String name, String baseUrl) {
        AiProviderEntity provider = aiProviderMapper.selectOne(new LambdaQueryWrapper<AiProviderEntity>()
                .eq(AiProviderEntity::getProviderCode, code)
                .last("LIMIT 1"));
        if (provider != null) {
            return provider;
        }
        provider = new AiProviderEntity();
        provider.setProviderCode(code);
        provider.setProviderName(name);
        provider.setBaseUrl(baseUrl);
        provider.setStatus("ENABLED");
        aiProviderMapper.insert(provider);
        return provider;
    }

    private void ensureModel(
            AiProviderEntity provider,
            String modelCode,
            String modelName,
            String modelType,
            List<String> capabilityTypes,
            Integer maxTokens,
            BigDecimal temperatureDefault
    ) {
        AiModelEntity existing = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
                .eq(AiModelEntity::getProviderId, provider.getId())
                .eq(AiModelEntity::getModelCode, modelCode)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }

        AiModelEntity model = new AiModelEntity();
        model.setProviderId(provider.getId());
        model.setModelCode(modelCode);
        model.setModelName(modelName);
        model.setModelType(modelType);
        model.setMaxTokens(maxTokens);
        model.setTemperatureDefault(temperatureDefault);
        model.setStatus("ENABLED");
        aiModelMapper.insert(model);

        for (String capabilityType : capabilityTypes) {
            AiModelCapabilityEntity capability = new AiModelCapabilityEntity();
            capability.setModelId(model.getId());
            capability.setCapabilityType(capabilityType);
            capability.setEnabled(Boolean.TRUE);
            aiModelCapabilityMapper.insert(capability);
        }
    }
}
