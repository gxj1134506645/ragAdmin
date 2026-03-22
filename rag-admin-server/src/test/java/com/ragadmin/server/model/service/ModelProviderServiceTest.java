package com.ragadmin.server.model.service;

import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.model.dto.ModelProviderHealthCheckResponse;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelProviderServiceTest {

    @Mock
    private AiProviderMapper aiProviderMapper;

    @Mock
    private AiModelMapper aiModelMapper;

    @Mock
    private AiModelCapabilityMapper aiModelCapabilityMapper;

    @Mock
    private ConversationChatClient conversationChatClient;

    @Mock
    private EmbeddingClientRegistry embeddingClientRegistry;

    private ModelProviderService modelProviderService;

    @BeforeEach
    void setUp() {
        modelProviderService = new ModelProviderService();
        ReflectionTestUtils.setField(modelProviderService, "aiProviderMapper", aiProviderMapper);
        ReflectionTestUtils.setField(modelProviderService, "aiModelMapper", aiModelMapper);
        ReflectionTestUtils.setField(modelProviderService, "aiModelCapabilityMapper", aiModelCapabilityMapper);
        ReflectionTestUtils.setField(modelProviderService, "conversationChatClient", conversationChatClient);
        ReflectionTestUtils.setField(modelProviderService, "embeddingClientRegistry", embeddingClientRegistry);
    }

    @Test
    void shouldReturnUpWhenProviderCapabilitiesAreHealthy() {
        AiProviderEntity provider = provider(1L, "BAILIAN", "百炼");
        AiModelEntity chatModel = model(10L, 1L, "qwen-max");
        AiModelEntity embeddingModel = model(11L, 1L, "text-embedding-v3");

        when(aiProviderMapper.selectById(1L)).thenReturn(provider);
        when(aiModelCapabilityMapper.selectModelIdsByCapabilityType("TEXT_GENERATION")).thenReturn(List.of(10L));
        when(aiModelCapabilityMapper.selectModelIdsByCapabilityType("EMBEDDING")).thenReturn(List.of(11L));
        when(aiModelMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(chatModel, embeddingModel);
        when(conversationChatClient.chat("BAILIAN", "qwen-max", List.of(new ChatPromptMessage("user", "ping"))))
                .thenReturn(new ChatCompletionResult("pong", 1, 1));
        when(embeddingClientRegistry.getClient("BAILIAN")).thenReturn(new EmbeddingModelClient() {
            @Override
            public boolean supports(String providerCode) {
                return true;
            }

            @Override
            public List<List<Float>> embed(String modelCode, List<String> inputs) {
                return List.of(List.of(0.1F, 0.2F));
            }
        });

        ModelProviderHealthCheckResponse response = modelProviderService.healthCheck(1L);

        assertEquals("UP", response.status());
        assertEquals(2, response.capabilityChecks().size());
        assertTrue(response.capabilityChecks().stream().allMatch(item -> "UP".equals(item.status())));
    }

    @Test
    void shouldReturnUnknownWhenProviderHasNoCapabilityModels() {
        AiProviderEntity provider = provider(2L, "BAILIAN", "百炼");

        when(aiProviderMapper.selectById(2L)).thenReturn(provider);
        when(aiModelCapabilityMapper.selectModelIdsByCapabilityType("TEXT_GENERATION")).thenReturn(List.of());
        when(aiModelCapabilityMapper.selectModelIdsByCapabilityType("EMBEDDING")).thenReturn(List.of());

        ModelProviderHealthCheckResponse response = modelProviderService.healthCheck(2L);

        assertEquals("UNKNOWN", response.status());
        assertTrue(response.capabilityChecks().stream().allMatch(item -> "UNKNOWN".equals(item.status())));
    }

    private AiProviderEntity provider(Long id, String code, String name) {
        AiProviderEntity entity = new AiProviderEntity();
        entity.setId(id);
        entity.setProviderCode(code);
        entity.setProviderName(name);
        return entity;
    }

    private AiModelEntity model(Long id, Long providerId, String code) {
        AiModelEntity entity = new AiModelEntity();
        entity.setId(id);
        entity.setProviderId(providerId);
        entity.setModelCode(code);
        entity.setStatus("ENABLED");
        return entity;
    }
}
