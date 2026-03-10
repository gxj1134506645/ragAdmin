package com.ragadmin.server.model.service;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import com.ragadmin.server.infra.ai.chat.ChatClientRegistry;
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.model.dto.ModelHealthCheckResponse;
import com.ragadmin.server.model.entity.AiModelCapabilityEntity;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelServiceTest {

    @Mock
    private AiModelMapper aiModelMapper;

    @Mock
    private AiProviderMapper aiProviderMapper;

    @Mock
    private AiModelCapabilityMapper aiModelCapabilityMapper;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private ChatClientRegistry chatClientRegistry;

    @Mock
    private EmbeddingClientRegistry embeddingClientRegistry;

    private ModelService modelService;

    @BeforeEach
    void setUp() {
        modelService = new ModelService();
        ReflectionTestUtils.setField(modelService, "aiModelMapper", aiModelMapper);
        ReflectionTestUtils.setField(modelService, "aiProviderMapper", aiProviderMapper);
        ReflectionTestUtils.setField(modelService, "aiModelCapabilityMapper", aiModelCapabilityMapper);
        ReflectionTestUtils.setField(modelService, "modelProviderService", modelProviderService);
        ReflectionTestUtils.setField(modelService, "chatClientRegistry", chatClientRegistry);
        ReflectionTestUtils.setField(modelService, "embeddingClientRegistry", embeddingClientRegistry);
    }

    @Test
    void shouldRejectWhenModelCapabilityDoesNotMatch() {
        AiModelEntity model = new AiModelEntity();
        model.setId(1L);
        model.setProviderId(10L);

        when(aiModelMapper.selectById(1L)).thenReturn(model);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(1L)))
                .thenReturn(List.of(capability(1L, "TEXT_GENERATION")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> modelService.requireModelWithCapability(1L, "EMBEDDING")
        );

        assertEquals("MODEL_CAPABILITY_INVALID", exception.getCode());
    }

    @Test
    void shouldReturnEmbeddingDescriptor() {
        AiModelEntity model = new AiModelEntity();
        model.setId(2L);
        model.setProviderId(20L);
        model.setModelCode("nomic-embed-text");

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(20L);
        provider.setProviderCode("OLLAMA");
        provider.setProviderName("Ollama");

        when(aiModelMapper.selectById(2L)).thenReturn(model);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(2L)))
                .thenReturn(List.of(capability(2L, "EMBEDDING")));
        when(aiProviderMapper.selectById(20L)).thenReturn(provider);

        EmbeddingModelDescriptor descriptor = modelService.requireEmbeddingModelDescriptor(2L);

        assertEquals(2L, descriptor.modelId());
        assertEquals("nomic-embed-text", descriptor.modelCode());
        assertEquals("OLLAMA", descriptor.providerCode());
        assertEquals("Ollama", descriptor.providerName());
    }

    @Test
    void shouldReturnUpWhenAllHealthChecksPass() {
        AiModelEntity model = new AiModelEntity();
        model.setId(3L);
        model.setProviderId(30L);
        model.setModelCode("qwen2.5:7b");

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(30L);
        provider.setProviderCode("OLLAMA");
        provider.setProviderName("Ollama");

        ChatModelClient chatClient = new ChatModelClient() {
            @Override
            public boolean supports(String providerCode) {
                return true;
            }

            @Override
            public ChatCompletionResult chat(String modelCode, List<ChatMessage> messages) {
                return new ChatCompletionResult("pong", 1, 1);
            }
        };

        EmbeddingModelClient embeddingClient = new EmbeddingModelClient() {
            @Override
            public boolean supports(String providerCode) {
                return true;
            }

            @Override
            public List<List<Float>> embed(String modelCode, List<String> inputs) {
                return List.of(List.of(0.1F, 0.2F));
            }
        };

        when(aiModelMapper.selectById(3L)).thenReturn(model);
        when(aiProviderMapper.selectById(30L)).thenReturn(provider);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(3L)))
                .thenReturn(List.of(
                        capability(3L, "TEXT_GENERATION"),
                        capability(3L, "EMBEDDING")
                ));
        when(chatClientRegistry.getClient("OLLAMA")).thenReturn(chatClient);
        when(embeddingClientRegistry.getClient("OLLAMA")).thenReturn(embeddingClient);

        ModelHealthCheckResponse response = modelService.healthCheck(3L);

        assertEquals("UP", response.status());
        assertEquals("模型探活成功", response.message());
        assertEquals(2, response.capabilityChecks().size());
        assertTrue(response.capabilityChecks().stream().allMatch(item -> "UP".equals(item.status())));
    }

    @Test
    void shouldReturnDownWhenAnyHealthCheckFails() {
        AiModelEntity model = new AiModelEntity();
        model.setId(4L);
        model.setProviderId(40L);
        model.setModelCode("qwen-bad");

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(40L);
        provider.setProviderCode("OLLAMA");
        provider.setProviderName("Ollama");

        ChatModelClient chatClient = new ChatModelClient() {
            @Override
            public boolean supports(String providerCode) {
                return true;
            }

            @Override
            public ChatCompletionResult chat(String modelCode, List<ChatMessage> messages) {
                throw new IllegalStateException("聊天探活失败");
            }
        };

        when(aiModelMapper.selectById(4L)).thenReturn(model);
        when(aiProviderMapper.selectById(40L)).thenReturn(provider);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(4L)))
                .thenReturn(List.of(capability(4L, "TEXT_GENERATION")));
        when(chatClientRegistry.getClient("OLLAMA")).thenReturn(chatClient);

        ModelHealthCheckResponse response = modelService.healthCheck(4L);

        assertEquals("DOWN", response.status());
        assertTrue(response.message().contains("模型探活失败"));
        assertEquals(1, response.capabilityChecks().size());
        assertEquals("DOWN", response.capabilityChecks().getFirst().status());
        assertTrue(response.capabilityChecks().getFirst().message().contains("聊天探活失败"));
    }

    private AiModelCapabilityEntity capability(Long modelId, String capabilityType) {
        AiModelCapabilityEntity entity = new AiModelCapabilityEntity();
        entity.setModelId(modelId);
        entity.setCapabilityType(capabilityType);
        entity.setEnabled(Boolean.TRUE);
        return entity;
    }
}
