package com.ragadmin.server.model.service;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingExecutionMode;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.knowledge.mapper.KnowledgeBaseMapper;
import com.ragadmin.server.model.dto.BatchDeleteModelsRequest;
import com.ragadmin.server.model.dto.CreateModelRequest;
import com.ragadmin.server.model.dto.ModelBatchDeleteResponse;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ModelProviderService modelProviderService;

    @Mock
    private ConversationChatClient conversationChatClient;

    @Mock
    private EmbeddingClientRegistry embeddingClientRegistry;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ModelService modelService;

    @BeforeEach
    void setUp() {
        modelService = new ModelService();
        ReflectionTestUtils.setField(modelService, "aiModelMapper", aiModelMapper);
        ReflectionTestUtils.setField(modelService, "aiProviderMapper", aiProviderMapper);
        ReflectionTestUtils.setField(modelService, "aiModelCapabilityMapper", aiModelCapabilityMapper);
        ReflectionTestUtils.setField(modelService, "knowledgeBaseMapper", knowledgeBaseMapper);
        ReflectionTestUtils.setField(modelService, "chunkVectorRefMapper", chunkVectorRefMapper);
        ReflectionTestUtils.setField(modelService, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(modelService, "modelProviderService", modelProviderService);
        ReflectionTestUtils.setField(modelService, "conversationChatClient", conversationChatClient);
        ReflectionTestUtils.setField(modelService, "embeddingClientRegistry", embeddingClientRegistry);
        ReflectionTestUtils.setField(modelService, "transactionTemplate", transactionTemplate);

        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
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
        assertEquals(EmbeddingExecutionMode.SYNC_TEXT, descriptor.executionMode());
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
        assertTrue(exception.getMessage().contains("input.url"));
    }

    @Test
    void shouldRejectDashScopeVisionEmbeddingModelWhenCreating() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(71L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        CreateModelRequest request = new CreateModelRequest();
        request.setProviderId(71L);
        request.setModelCode("tongyi-embedding-vision-plus");
        request.setModelName("通义视觉向量");
        request.setCapabilityTypes(List.of("EMBEDDING"));
        request.setModelType("EMBEDDING");
        request.setStatus("ENABLED");

        when(modelProviderService.requireProvider(71L)).thenReturn(provider);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> modelService.create(request)
        );

        assertEquals("EMBEDDING_MODEL_UNSUPPORTED", exception.getCode());
        verify(aiModelMapper, never()).insert(any(AiModelEntity.class));
    }

    @Test
    void shouldAllowDashScopeAsyncEmbeddingModelWhenCreating() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(72L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        CreateModelRequest request = new CreateModelRequest();
        request.setProviderId(72L);
        request.setModelCode(" text-embedding-async-v2 ");
        request.setModelName("通义异步文本向量");
        request.setCapabilityTypes(List.of("EMBEDDING"));
        request.setModelType("EMBEDDING");
        request.setStatus("ENABLED");

        when(modelProviderService.requireProvider(72L)).thenReturn(provider);
        doAnswer(invocation -> {
            AiModelEntity entity = invocation.getArgument(0);
            entity.setId(172L);
            return 1;
        }).when(aiModelMapper).insert(any(AiModelEntity.class));

        ModelResponse response = modelService.create(request);

        assertEquals("text-embedding-async-v2", response.modelCode());
    }

    @Test
    void shouldReturnAsyncBatchDescriptorForDashScopeAsyncEmbeddingModel() {
        AiModelEntity model = new AiModelEntity();
        model.setId(73L);
        model.setProviderId(173L);
        model.setModelCode("text-embedding-async-v2");

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(173L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        when(aiModelMapper.selectById(73L)).thenReturn(model);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(73L)))
                .thenReturn(List.of(capability(73L, "EMBEDDING")));
        when(aiProviderMapper.selectById(173L)).thenReturn(provider);

        EmbeddingModelDescriptor descriptor = modelService.requireEmbeddingModelDescriptor(73L);

        assertEquals("text-embedding-async-v2", descriptor.modelCode());
        assertEquals(EmbeddingExecutionMode.ASYNC_BATCH, descriptor.executionMode());
    }

    @Test
    void shouldRejectAsyncEmbeddingModelForKnowledgeBasePipeline() {
        AiModelEntity model = new AiModelEntity();
        model.setId(74L);
        model.setProviderId(174L);
        model.setModelCode("text-embedding-async-v2");

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(174L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        when(aiModelMapper.selectById(74L)).thenReturn(model);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(74L)))
                .thenReturn(List.of(capability(74L, "EMBEDDING")));
        when(aiProviderMapper.selectById(174L)).thenReturn(provider);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> modelService.requireKnowledgeBaseEmbeddingModelDescriptor(74L)
        );

        assertEquals("EMBEDDING_MODEL_EXECUTION_MODE_UNSUPPORTED", exception.getCode());
    }

    @Test
    void shouldRejectWhenDefaultChatModelIsNotConfiguredInDatabase() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> modelService.resolveChatModelDescriptor(null)
        );

        assertEquals("DEFAULT_CHAT_MODEL_NOT_CONFIGURED", exception.getCode());
    }

    @Test
    void shouldResolveDatabaseDefaultChatModelWhenConfigured() {
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(80L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        AiModelEntity model = new AiModelEntity();
        model.setId(8L);
        model.setProviderId(80L);
        model.setModelCode("qwen-plus");
        model.setStatus("ENABLED");
        model.setIsDefaultChatModel(Boolean.TRUE);

        when(aiModelMapper.selectList(any())).thenReturn(List.of(model));
        when(aiModelMapper.selectById(8L)).thenReturn(model);
        when(aiProviderMapper.selectById(80L)).thenReturn(provider);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(8L)))
                .thenReturn(List.of(capability(8L, "TEXT_GENERATION")));

        ModelService.ChatModelDescriptor descriptor = modelService.resolveChatModelDescriptor(null);

        assertEquals(8L, descriptor.modelId());
        assertEquals("qwen-plus", descriptor.modelCode());
        assertEquals("BAILIAN", descriptor.providerCode());
    }

    @Test
    void shouldRejectWhenUpdatingDefaultChatModelToDisabled() {
        AiModelEntity entity = new AiModelEntity();
        entity.setId(12L);
        entity.setProviderId(12L);
        entity.setModelCode("qwen-max");
        entity.setModelName("通义千问 Max");
        entity.setModelType("CHAT");
        entity.setStatus("ENABLED");
        entity.setIsDefaultChatModel(Boolean.TRUE);

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(12L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        UpdateModelRequest request = new UpdateModelRequest();
        request.setProviderId(12L);
        request.setModelCode("qwen-max");
        request.setModelName("通义千问 Max");
        request.setCapabilityTypes(List.of("TEXT_GENERATION"));
        request.setModelType("CHAT");
        request.setStatus("DISABLED");

        when(aiModelMapper.selectById(12L)).thenReturn(entity);
        when(modelProviderService.requireProvider(12L)).thenReturn(provider);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> modelService.update(12L, request)
        );

        assertEquals("DEFAULT_CHAT_MODEL_CHANGE_FORBIDDEN", exception.getCode());
    }

    @Test
    void shouldSetDefaultChatModel() {
        AiModelEntity model = new AiModelEntity();
        model.setId(9L);
        model.setProviderId(90L);
        model.setModelCode("qwen-max");
        model.setModelName("通义千问 Max");
        model.setModelType("CHAT");
        model.setStatus("ENABLED");
        model.setIsDefaultChatModel(Boolean.FALSE);

        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(90L);
        provider.setProviderCode("BAILIAN");
        provider.setProviderName("百炼");

        when(aiModelMapper.selectById(9L)).thenReturn(model);
        when(aiProviderMapper.selectById(90L)).thenReturn(provider);
        when(aiModelCapabilityMapper.selectEnabledByModelIds(List.of(9L)))
                .thenReturn(List.of(capability(9L, "TEXT_GENERATION")));

        ModelResponse response = modelService.setDefaultChatModel(9L);

        assertTrue(response.isDefaultChatModel());
        assertEquals(9L, response.id());
        assertEquals("qwen-max", response.modelCode());
        verify(aiModelMapper).update(any(AiModelEntity.class), any());
        verify(aiModelMapper).updateById(model);
    }

    @Test
    void shouldRejectDeletingDefaultChatModel() {
        AiModelEntity model = new AiModelEntity();
        model.setId(13L);
        model.setModelName("通义千问 Max");
        model.setIsDefaultChatModel(Boolean.TRUE);

        when(aiModelMapper.selectById(13L)).thenReturn(model);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> modelService.delete(13L)
        );

        assertEquals("DEFAULT_CHAT_MODEL_DELETE_FORBIDDEN", exception.getCode());
    }

    @Test
    void shouldBatchDeleteModelsAndReturnFailureSummary() {
        AiModelEntity deletableModel = new AiModelEntity();
        deletableModel.setId(21L);
        deletableModel.setModelName("可删除模型");
        deletableModel.setIsDefaultChatModel(Boolean.FALSE);

        AiModelEntity defaultChatModel = new AiModelEntity();
        defaultChatModel.setId(22L);
        defaultChatModel.setModelName("默认聊天模型");
        defaultChatModel.setIsDefaultChatModel(Boolean.TRUE);

        BatchDeleteModelsRequest request = new BatchDeleteModelsRequest();
        request.setModelIds(List.of(21L, 22L));

        when(aiModelMapper.selectById(21L)).thenReturn(deletableModel);
        when(aiModelMapper.selectById(22L)).thenReturn(defaultChatModel);

        ModelBatchDeleteResponse response = modelService.batchDelete(request);

        assertEquals(2, response.requestedCount());
        assertEquals(1, response.successCount());
        assertEquals(1, response.failedCount());
        assertEquals(List.of(21L), response.deletedIds());
        assertEquals(1, response.failedItems().size());
        assertEquals(22L, response.failedItems().getFirst().modelId());
        assertTrue(response.failedItems().getFirst().message().contains("默认聊天模型"));
        verify(aiModelCapabilityMapper, times(1)).delete(any());
        verify(aiModelMapper).deleteById(21L);
    }

    @Test
    void shouldMarkFailureWhenBatchDeleteModelIsInUse() {
        AiModelEntity model = new AiModelEntity();
        model.setId(23L);
        model.setModelName("引用中模型");
        model.setIsDefaultChatModel(Boolean.FALSE);

        BatchDeleteModelsRequest request = new BatchDeleteModelsRequest();
        request.setModelIds(List.of(23L));

        when(aiModelMapper.selectById(23L)).thenReturn(model);
        when(knowledgeBaseMapper.selectCount(any())).thenReturn(1L);

        ModelBatchDeleteResponse response = modelService.batchDelete(request);

        assertEquals(0, response.successCount());
        assertEquals(1, response.failedCount());
        assertEquals("引用中模型", response.failedItems().getFirst().modelName());
        assertTrue(response.failedItems().getFirst().message().contains("知识库引用"));
        verify(aiModelMapper, never()).deleteById(23L);
    }

    @Test
    void shouldRequireExplicitEmbeddingModelWhenResolvingDescriptor() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> modelService.resolveEmbeddingModelDescriptor(null)
        );

        assertEquals("EMBEDDING_MODEL_REQUIRED", exception.getCode());
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
        when(conversationChatClient.chat("OLLAMA", "qwen2.5:7b", List.of(new ChatPromptMessage("user", "ping"))))
                .thenReturn(new ChatCompletionResult("pong", 1, 1));
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
        when(conversationChatClient.chat("OLLAMA", "qwen-bad", List.of(new ChatPromptMessage("user", "ping"))))
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
