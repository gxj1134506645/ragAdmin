package com.ragadmin.server.retrieval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.retrieval.config.RerankingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RerankingServiceTest {

    private RerankingService rerankingService;

    @BeforeEach
    void setUp() throws Exception {
        rerankingService = new RerankingService();
        RerankingProperties properties = new RerankingProperties();
        properties.setEnabled(true);
        properties.setTopN(3);
        properties.setMaxCandidates(20);

        java.lang.reflect.Field propsField = RerankingService.class.getDeclaredField("rerankingProperties");
        propsField.setAccessible(true);
        propsField.set(rerankingService, properties);

        java.lang.reflect.Field mapperField = RerankingService.class.getDeclaredField("objectMapper");
        mapperField.setAccessible(true);
        mapperField.set(rerankingService, new ObjectMapper());
    }

    @Test
    void shouldParseScoredPassagesCorrectly() {
        String json = """
                [{"index": 1, "score": 9.5}, {"index": 2, "score": 7.0}, {"index": 3, "score": 3.5}]
                """;

        List<RerankingService.ScoredPassage> scores = rerankingService.parseScoredPassages(json);

        assertEquals(3, scores.size());
        // Should be sorted by score descending
        assertEquals(9.5, scores.get(0).score(), 0.001);
        assertEquals(7.0, scores.get(1).score(), 0.001);
        assertEquals(3.5, scores.get(2).score(), 0.001);
        // Indexes are 0-based after conversion
        assertEquals(0, scores.get(0).index());
        assertEquals(1, scores.get(1).index());
        assertEquals(2, scores.get(2).index());
    }

    @Test
    void shouldParseJsonWithSurroundingText() {
        String response = "Here are the scores:\n[{\"index\": 1, \"score\": 8.0}]\nDone.";

        List<RerankingService.ScoredPassage> scores = rerankingService.parseScoredPassages(response);

        assertEquals(1, scores.size());
        assertEquals(8.0, scores.getFirst().score(), 0.001);
    }

    @Test
    void shouldHandleEmptyResponse() {
        List<RerankingService.ScoredPassage> scores = rerankingService.parseScoredPassages("");
        assertTrue(scores.isEmpty());
    }

    @Test
    void shouldHandleNullResponse() {
        List<RerankingService.ScoredPassage> scores = rerankingService.parseScoredPassages(null);
        assertTrue(scores.isEmpty());
    }

    @Test
    void shouldHandleMalformedJson() {
        List<RerankingService.ScoredPassage> scores = rerankingService.parseScoredPassages("not json at all");
        assertTrue(scores.isEmpty());
    }

    @Test
    void shouldReturnSingleCandidateUnchanged() {
        List<RetrievalService.RetrievedChunk> single = List.of(
                new RetrievalService.RetrievedChunk(chunk(1L, "文本"), 0.9)
        );

        List<RetrievalService.RetrievedChunk> result = rerankingService.rerank("查询", single);

        assertEquals(1, result.size());
        assertEquals(0.9, result.getFirst().score(), 0.001);
    }

    private ChunkEntity chunk(Long id, String text) {
        ChunkEntity c = new ChunkEntity();
        c.setId(id);
        c.setChunkText(text);
        c.setKbId(1L);
        c.setDocumentId(1L);
        c.setChunkNo(1);
        return c;
    }
}
