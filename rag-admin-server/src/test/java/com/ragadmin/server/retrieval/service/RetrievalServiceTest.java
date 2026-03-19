package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.ChunkVectorRefEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.infra.vector.MilvusVectorStoreClient;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.config.RetrievalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private ModelService modelService;

    @Mock
    private EmbeddingClientRegistry embeddingClientRegistry;

    @Mock
    private MilvusVectorStoreClient milvusVectorStoreClient;

    @Mock
    private ChunkVectorRefMapper chunkVectorRefMapper;

    @Mock
    private ChunkMapper chunkMapper;

    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new RetrievalService();
        ReflectionTestUtils.setField(retrievalService, "modelService", modelService);
        ReflectionTestUtils.setField(retrievalService, "embeddingClientRegistry", embeddingClientRegistry);
        ReflectionTestUtils.setField(retrievalService, "milvusVectorStoreClient", milvusVectorStoreClient);
        ReflectionTestUtils.setField(retrievalService, "chunkVectorRefMapper", chunkVectorRefMapper);
        ReflectionTestUtils.setField(retrievalService, "chunkMapper", chunkMapper);

        RetrievalProperties retrievalProperties = new RetrievalProperties();
        retrievalProperties.setDefaultTopK(5);
        retrievalProperties.setMaxContextChunks(2);
        retrievalProperties.setMaxContextChars(500);
        retrievalProperties.setReferenceSnippetChars(10);
        ReflectionTestUtils.setField(retrievalService, "retrievalProperties", retrievalProperties);
    }

    @Test
    void shouldLimitContextByConfiguredChunkCount() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(1L);
        knowledgeBase.setEmbeddingModelId(10L);
        knowledgeBase.setRetrieveTopK(3);

        ChunkVectorRefEntity sampleRef = new ChunkVectorRefEntity();
        sampleRef.setCollectionName("kb_1");

        ChunkVectorRefEntity ref1 = new ChunkVectorRefEntity();
        ref1.setChunkId(101L);
        ref1.setVectorId("v1");

        ChunkVectorRefEntity ref2 = new ChunkVectorRefEntity();
        ref2.setChunkId(102L);
        ref2.setVectorId("v2");

        ChunkVectorRefEntity ref3 = new ChunkVectorRefEntity();
        ref3.setChunkId(103L);
        ref3.setVectorId("v3");

        ChunkEntity chunk1 = chunk(101L, 1, "第一段内容。");
        ChunkEntity chunk2 = chunk(102L, 2, "第二段内容。");
        ChunkEntity chunk3 = chunk(103L, 3, "第三段内容。");

        when(chunkVectorRefMapper.selectOne(any())).thenReturn(sampleRef);
        when(modelService.resolveEmbeddingModelDescriptor(10L))
                .thenReturn(new EmbeddingModelDescriptor(10L, "text-embedding-v3", "BAILIAN", "百炼"));
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
        when(milvusVectorStoreClient.search("kb_1", List.of(0.1F, 0.2F), 3))
                .thenReturn(List.of(
                        new MilvusVectorStoreClient.SearchResult("v1", 0.9D),
                        new MilvusVectorStoreClient.SearchResult("v2", 0.8D),
                        new MilvusVectorStoreClient.SearchResult("v3", 0.7D)
                ));
        when(chunkVectorRefMapper.selectByVectorIds(List.of("v1", "v2", "v3"))).thenReturn(List.of(ref1, ref2, ref3));
        when(chunkMapper.selectBatchIds(List.of(101L, 102L, 103L))).thenReturn(List.of(chunk1, chunk2, chunk3));

        RetrievalService.RetrievalResult result = retrievalService.retrieve(knowledgeBase, "什么内容");

        assertEquals(3, result.chunks().size());
        assertFalse(result.context().contains("片段3"));
        assertTrueContains(result.context(), "片段1");
        assertTrueContains(result.context(), "片段2");
    }

    @Test
    void shouldLimitReferenceSnippetLength() {
        ChunkEntity chunk = chunk(201L, 1, "0123456789ABCDEFGHIJ");
        List<ChatReferenceResponse> responses = retrievalService.toReferenceResponses(
                List.of(new RetrievalService.RetrievedChunk(chunk, 0.8D)),
                documentId -> "文档"
        );

        assertEquals(1, responses.size());
        assertEquals(900L, responses.getFirst().kbId());
        assertEquals("0123456789", responses.getFirst().contentSnippet());
    }

    private void assertTrueContains(String actual, String expected) {
        if (!actual.contains(expected)) {
            throw new AssertionError("expected context to contain: " + expected + ", actual=" + actual);
        }
    }

    private ChunkEntity chunk(Long id, int chunkNo, String text) {
        ChunkEntity entity = new ChunkEntity();
        entity.setId(id);
        entity.setKbId(900L);
        entity.setDocumentId(1000L);
        entity.setChunkNo(chunkNo);
        entity.setChunkText(text);
        return entity;
    }
}
