package com.ragadmin.server.model.service;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import com.ragadmin.server.infra.ai.AiProperties;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import com.ragadmin.server.knowledge.mapper.KnowledgeBaseMapper;
import com.ragadmin.server.model.dto.CreateModelRequest;
import com.ragadmin.server.model.dto.ModelHealthCheckResponse;
import com.ragadmin.server.model.dto.ModelResponse;
import com.ragadmin.server.model.dto.UpdateModelRequest;
import com.ragadmin.server.model.entity.AiModelCapabilityEntity;
import com.ragadmin.server.model.entity.AiModelEntity;
import com.ragadmin.server.model.entity.AiProviderEntity;
import com.ragadmin.server.model.mapper.AiModelCapabilityMapper;
import com.ragadmin.server.model.mapper.AiModelMapper;
import com.ragadmin.server.model.mapper.AiProviderMapper;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
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
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private ChunkVectorRefMapper chunkVectorRefMapper;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private ConversationChatClient conversationChatClient;

    @Mock
    private EmbeddingClientRegistry embeddingClientRegistry;

    private final AiProperties aiProperties = new AiProperties();
    private final BailianProperties bailianProperties = new BailianProperties();
    private final OllamaProperties ollamaProperties = new OllamaProperties();

    private ModelService modelService;

    @BeforeEach
    void setUp() {
        modelService = new ModelService();
        ReflectionTestUtils.setField(modelService, "aiModelMapper", aiModelMapper);
        ReflectionTestUtils.setField(modelService, "aiProviderMapper", aiProviderMapper);
        ReflectionTestUtils.setField(modelService, "aiModelCapabilityMapper", aiModelCapabilityMapper);
        ReflectionTestUtils.setField(modelService, "knowledgeBaseMapper", knowledgeBaseMapper);
        ReflectionTestUtils.setField(modelService, "chunkVectorRefMapper", chunkVectorRefMapper);
        ReflectionTestUtils.setField(modelService, "modelProviderService", modelProviderService);
        ReflectionTestUtils.setField(modelService, "conversationChatClient", conversationChatClient);
        ReflectionTestUtils.setField(modelService, "embeddingClientRegistry", embeddingClientRegistry);
        ReflectionTestUtils.setField(modelService, "aiProperties", aiProperties);
        ReflectionTestUtils.setField(modelService, "bailianProperties", bailianProperties);
        ReflectionTestUtils.setField(modelService, "ollamaProperties", ollamaProperties);
    }

    @Test
    void shouldClearGenerationOptionsWhenCreatingEmbeddingModel() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(10L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        CreateModelRequest request = new CreateModelRequest();
        request.setProviderId(10L);
        request.setModelCode("text-embedding-v3");
        request.setModelName("通义文本向量");
        request.setCapabilityTypes(List.of("EMBEDDING"));
        request.setModelType("EMBEDDING");
        request.setMaxTokens(8192);
        request.setTemperatureDefault(new BigDecimal("0.5"));
        request.setStatus("ENABLED");

        when(modelProviderService.requireProvider(10L)).thenReturn(provider);
        doAnswer(invocation -> {
            AiModelEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(aiModelMapper).insert(any(AiModelEntity.class));

        ModelResponse response = modelService.create(request);

        ArgumentCaptor<AiModelEntity> captor = ArgumentCaptor.forClass(AiModelEntity.class);
        verify(aiModelMapper).insert(captor.capture());
        assertNull(captor.getValue().getMaxTokens());
        assertNull(captor.getValue().getTemperatureDefault());
        assertNull(response.maxTokens());
        assertNull(response.temperatureDefault());
    }

    @Test
    void shouldClearGenerationOptionsWhenUpdatingEmbeddingModel() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(11L);
        provider.setProviderCode("OLLAMA");
        provider.setProviderName("Ollama");

        AiModelEntity entity = new AiModelEntity();
        entity.setId(11L);
        entity.setProviderId(11L);
        entity.setModelCode("legacy-chat-model");
        entity.setModelName("旧模型");
        entity.setModelType("CHAT");
        entity.setMaxTokens(4096);
        entity.setTemperatureDefault(new BigDecimal("0.8"));
        entity.setStatus("ENABLED");

        UpdateModelRequest request = new UpdateModelRequest();
        request.setProviderId(11L);
        request.setModelCode("nomic-embed-text");
        request.setModelName("Nomic Embed");
        request.setCapabilityTypes(List.of("EMBEDDING"));
        request.setModelType("EMBEDDING");
        request.setMaxTokens(2048);
        request.setTemperatureDefault(new BigDecimal("0.2"));
        request.setStatus("ENABLED");

        when(aiModelMapper.selectById(11L)).thenReturn(entity);
        when(modelProviderService.requireProvider(11L)).thenReturn(provider);
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(0L);

        ModelResponse response = modelService.update(11L, request);

        ArgumentCaptor<AiModelEntity> captor = ArgumentCaptor.forClass(AiModelEntity.class);
        verify(aiModelMapper).updateById(captor.capture());
        assertNull(captor.getValue().getMaxTokens());
        assertNull(captor.getValue().getTemperatureDefault());
        assertNull(response.maxTokens());
        assertNull(response.temperatureDefault());
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
    void shouldRejectDashScopeMultimodalEmbeddingModelForTextPipeline() {
        AiModelEntity model = new AiModelEntity();
        model.setId(7L);
        model.setProviderId(70L);
        model.setModelCode("qwen2.5-vl-embedding");

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(70L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        when(aiModelMapper.selectById(7L)).thenReturn(model);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(7L)))
                .thenReturn(List.of(capability(7L, "EMBEDDING")));
        when(aiProviderMapper.selectById(70L)).thenReturn(provider);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> modelService.requireEmbeddingModelDescriptor(7L)
        );

        assertEquals("EMBEDDING_MODEL_UNSUPPORTED", exception.getCode());
        assertTrue(exception.getMessage().contains("text-embedding-v3"));
    }

    @Test
    void shouldResolveDefaultChatModelDescriptorWhenKnowledgeBaseModelIsEmpty() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(50L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        AiModelEntity model = new AiModelEntity();
        model.setId(5L);
        model.setProviderId(50L);
        model.setModelCode("qwen-max");

        aiProperties.setDefaultProvider("BAILIAN");
        bailianProperties.setDefaultChatModel("qwen-max");

        when(aiProviderMapper.selectOne(any())).thenReturn(provider);
        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(aiModelMapper.selectById(5L)).thenReturn(model);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(5L)))
                .thenReturn(List.of(capability(5L, "TEXT_GENERATION")));

        ModelService.ChatModelDescriptor descriptor = modelService.resolveChatModelDescriptor(null);

        assertEquals(5L, descriptor.modelId());
        assertEquals("qwen-max", descriptor.modelCode());
        assertEquals("BAILIAN", descriptor.providerCode());
        assertEquals("百炼", descriptor.providerName());
    }

    @Test
    void shouldResolveDefaultEmbeddingModelDescriptorForOllama() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(60L);
        provider.setProviderCode("OLLAMA");
        provider.setProviderName("Ollama");

        AiModelEntity model = new AiModelEntity();
        model.setId(6L);
        model.setProviderId(60L);
        model.setModelCode("nomic-embed-text");

        aiProperties.setDefaultProvider("OLLAMA");
        ollamaProperties.setDefaultEmbeddingModel("nomic-embed-text");

        when(aiProviderMapper.selectOne(any())).thenReturn(provider);
        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(aiModelMapper.selectById(6L)).thenReturn(model);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(6L)))
                .thenReturn(List.of(capability(6L, "EMBEDDING")));

        EmbeddingModelDescriptor descriptor = modelService.resolveEmbeddingModelDescriptor(null);

        assertEquals(6L, descriptor.modelId());
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
        when(conversationChatClient.chat("OLLAMA", "qwen2.5:7b", List.of(new ChatModelClient.ChatMessage("user", "ping"))))
                .thenReturn(new ChatModelClient.ChatCompletionResult("pong", 1, 1));
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

        when(aiModelMapper.selectById(4L)).thenReturn(model);
        when(aiProviderMapper.selectById(40L)).thenReturn(provider);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(4L)))
                .thenReturn(List.of(capability(4L, "TEXT_GENERATION")));
        when(conversationChatClient.chat("OLLAMA", "qwen-bad", List.of(new ChatModelClient.ChatMessage("user", "ping"))))
                .thenThrow(new IllegalStateException("聊天探活失败"));

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
