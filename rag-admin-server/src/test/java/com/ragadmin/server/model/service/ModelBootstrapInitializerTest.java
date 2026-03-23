package com.ragadmin.server.model.service;

import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelBootstrapInitializerTest {

    @Mock
    private AiProviderMapper aiProviderMapper;

    @Mock
    private AiModelMapper aiModelMapper;

    @Mock
    private AiModelCapabilityMapper aiModelCapabilityMapper;

    @Test
    void shouldBootstrapConfiguredDefaultEmbeddingModelWhenItDiffersFromBaseline() throws Exception {
        BailianProperties bailianProperties = new BailianProperties();
        bailianProperties.setEnabled(true);
        bailianProperties.setDefaultEmbeddingModel("text-embedding-v2");

        OllamaProperties ollamaProperties = new OllamaProperties();
        ollamaProperties.setEnabled(false);

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(1L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("阿里百炼");

        when(aiProviderMapper.selectOne(any())).thenReturn(provider);
        when(aiModelMapper.selectOne(any())).thenReturn(null);

        AtomicLong idGenerator = new AtomicLong(100L);
        doAnswer(invocation -> {
            AiModelEntity entity = invocation.getArgument(0);
            entity.setId(idGenerator.getAndIncrement());
            return 1;
        }).when(aiModelMapper).insert(any(AiModelEntity.class));

        ModelBootstrapInitializer initializer = new ModelBootstrapInitializer(
                aiProviderMapper,
                aiModelMapper,
                aiModelCapabilityMapper,
                bailianProperties,
                ollamaProperties
        );

        initializer.run(null);

        ArgumentCaptor<AiModelEntity> modelCaptor = ArgumentCaptor.forClass(AiModelEntity.class);
        verify(aiModelMapper, atLeastOnce()).insert(modelCaptor.capture());

        assertTrue(modelCaptor.getAllValues().stream()
                .anyMatch(model -> "text-embedding-v2".equals(model.getModelCode())));
    }
}
