package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.ChunkVectorRefEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingExecutionMode;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Mock
    private KeywordRetrievalStrategy keywordRetrievalStrategy;

    @Mock
    private RrfFusionService rrfFusionService;

    @Mock
    private QueryRewritingService queryRewritingService;

    @Mock
    private RerankingService rerankingService;

    @Mock
    private ParentChunkExpansionService parentChunkExpansionService;

    @Mock
    private QueryPreprocessService queryPreprocessService;

    @Mock
    private ConflictDetectionService conflictDetectionService;

    private com.ragadmin.server.retrieval.config.RerankingProperties rerankingProperties;

    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new RetrievalService();
        ReflectionTestUtils.setField(retrievalService, "modelService", modelService);
        ReflectionTestUtils.setField(retrievalService, "embeddingClientRegistry", embeddingClientRegistry);
        ReflectionTestUtils.setField(retrievalService, "milvusVectorStoreClient", milvusVectorStoreClient);
        ReflectionTestUtils.setField(retrievalService, "chunkVectorRefMapper", chunkVectorRefMapper);
        ReflectionTestUtils.setField(retrievalService, "chunkMapper", chunkMapper);
        ReflectionTestUtils.setField(retrievalService, "keywordRetrievalStrategy", keywordRetrievalStrategy);
        ReflectionTestUtils.setField(retrievalService, "rrfFusionService", rrfFusionService);
        ReflectionTestUtils.setField(retrievalService, "queryRewritingService", queryRewritingService);
        ReflectionTestUtils.setField(retrievalService, "rerankingService", rerankingService);
        ReflectionTestUtils.setField(retrievalService, "parentChunkExpansionService", parentChunkExpansionService);
        ReflectionTestUtils.setField(retrievalService, "queryPreprocessService", queryPreprocessService);
        ReflectionTestUtils.setField(retrievalService, "conflictDetectionService", conflictDetectionService);

        rerankingProperties = new com.ragadmin.server.retrieval.config.RerankingProperties();
        rerankingProperties.setEnabled(false);
        ReflectionTestUtils.setField(retrievalService, "rerankingProperties", rerankingProperties);

        RetrievalProperties retrievalProperties = new RetrievalProperties();
        retrievalProperties.setDefaultTopK(5);
        retrievalProperties.setMaxContextChunks(2);
        retrievalProperties.setMaxContextChars(500);
        retrievalProperties.setReferenceSnippetChars(10);
        retrievalProperties.setRrfK(60);
        retrievalProperties.setHybridTopKMultiplier(2);
        ReflectionTestUtils.setField(retrievalService, "retrievalProperties", retrievalProperties);

        // 默认 query rewriting 返回原始查询（pass-through）
        lenient().when(queryRewritingService.rewrite(anyString(), any()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(0);
                    return new QueryRewritingService.RewrittenQueries(List.of(query));
                });

        // 默认 parent expansion 为 pass-through
        lenient().when(parentChunkExpansionService.expandToParents(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 默认 query preprocess 为 pass-through
        lenient().when(queryPreprocessService.preprocess(anyString()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(0);
                    return new com.ragadmin.server.retrieval.model.PreprocessResult(query, false, false);
                });

        // 默认 conflict detection 为 pass-through
        lenient().when(conflictDetectionService.detect(anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.List<com.ragadmin.server.retrieval.service.RetrievalService.RetrievedChunk> chunks = invocation.getArgument(1);
                    return new com.ragadmin.server.retrieval.model.ConflictDetectionResult(java.util.List.of(), chunks);
                });
    }

    @Test
    void shouldLimitContextByConfiguredChunkCount() {
        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(1L);
        knowledgeBase.setEmbeddingModelId(10L);
        knowledgeBase.setRetrieveTopK(3);
        knowledgeBase.setRetrievalMode("SEMANTIC_ONLY");

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
        when(modelService.resolveKnowledgeBaseEmbeddingModelDescriptor(10L))
                .thenReturn(new EmbeddingModelDescriptor(10L, "text-embedding-v3", "BAILIAN", "百炼", EmbeddingExecutionMode.SYNC_TEXT));
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
        // SEMANTIC_ONLY 不应调用 keyword 策略
        verify(keywordRetrievalStrategy, never()).retrieve(any(), anyString(), anyInt());
    }

    @Test
    void shouldDispatchToKeywordOnly() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(1L);
        kb.setRetrievalMode("KEYWORD_ONLY");

        ChunkEntity chunk1 = chunk(100L, 1, "关键词结果");
        List<RetrievalService.RetrievedChunk> keywordResults = List.of(
                new RetrievalService.RetrievedChunk(chunk1, 8.5)
        );
        when(keywordRetrievalStrategy.retrieve(eq(kb), eq("查询"), eq(5))).thenReturn(keywordResults);

        RetrievalService.RetrievalResult result = retrievalService.retrieve(kb, "查询");

        assertEquals(1, result.chunks().size());
        assertEquals(100L, result.chunks().getFirst().chunk().getId());
        verify(milvusVectorStoreClient, never()).search(any(), any(), anyInt());
    }

    @Test
    void shouldDispatchToHybrid() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(1L);
        kb.setEmbeddingModelId(10L);
        kb.setRetrieveTopK(3);
        kb.setRetrievalMode("HYBRID");

        ChunkEntity semChunk = chunk(100L, 1, "语义结果");
        ChunkEntity kwChunk = chunk(101L, 2, "关键词结果");
        List<RetrievalService.RetrievedChunk> semanticResults = List.of(
                new RetrievalService.RetrievedChunk(semChunk, 0.9)
        );
        List<RetrievalService.RetrievedChunk> keywordResults = List.of(
                new RetrievalService.RetrievedChunk(kwChunk, 8.5)
        );

        // HYBRID with topK=3, multiplier=2 => expandedTopK=6
        ChunkVectorRefEntity sampleRef = new ChunkVectorRefEntity();
        sampleRef.setCollectionName("kb_1");
        when(chunkVectorRefMapper.selectOne(any())).thenReturn(sampleRef);
        when(modelService.resolveKnowledgeBaseEmbeddingModelDescriptor(10L))
                .thenReturn(new EmbeddingModelDescriptor(10L, "text-embedding-v3", "BAILIAN", "百炼", EmbeddingExecutionMode.SYNC_TEXT));
        when(embeddingClientRegistry.getClient("BAILIAN")).thenReturn(new EmbeddingModelClient() {
            @Override
            public boolean supports(String providerCode) { return true; }
            @Override
            public List<List<Float>> embed(String modelCode, List<String> inputs) {
                return List.of(List.of(0.1F, 0.2F));
            }
        });
        when(milvusVectorStoreClient.search("kb_1", List.of(0.1F, 0.2F), 6))
                .thenReturn(List.of(new MilvusVectorStoreClient.SearchResult("v1", 0.9)));
        ChunkVectorRefEntity ref = new ChunkVectorRefEntity();
        ref.setChunkId(100L);
        ref.setVectorId("v1");
        when(chunkVectorRefMapper.selectByVectorIds(List.of("v1"))).thenReturn(List.of(ref));
        when(chunkMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(semChunk));

        when(keywordRetrievalStrategy.retrieve(eq(kb), eq("查询"), eq(6))).thenReturn(keywordResults);

        List<RetrievalService.RetrievedChunk> fusedResults = List.of(
                new RetrievalService.RetrievedChunk(kwChunk, 0.033),
                new RetrievalService.RetrievedChunk(semChunk, 0.016)
        );
        when(rrfFusionService.fuse(semanticResults, keywordResults, 60, 3)).thenReturn(fusedResults);

        RetrievalService.RetrievalResult result = retrievalService.retrieve(kb, "查询");

        assertEquals(2, result.chunks().size());
        verify(rrfFusionService).fuse(semanticResults, keywordResults, 60, 3);
    }

    @Test
    void shouldDefaultToHybridWhenModeIsNull() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(1L);
        kb.setEmbeddingModelId(10L);
        // retrievalMode is null => HYBRID (semantic + keyword + RRF)

        when(chunkVectorRefMapper.selectOne(any())).thenReturn(null);

        RetrievalService.RetrievalResult result = retrievalService.retrieve(kb, "查询");

        assertTrue(result.chunks().isEmpty());
        verify(keywordRetrievalStrategy).retrieve(any(), anyString(), anyInt());
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
