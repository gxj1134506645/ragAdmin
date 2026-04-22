package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParentChunkExpansionServiceTest {

    @Mock
    private ChunkMapper chunkMapper;

    private ParentChunkExpansionService service;

    @BeforeEach
    void setUp() {
        service = new ParentChunkExpansionService();
        ReflectionTestUtils.setField(service, "chunkMapper", chunkMapper);
    }

    @Test
    void shouldExpandChildChunksToParentText() {
        ChunkEntity parent = chunk(100L, "父块全文内容，包含完整的上下文信息");
        ChunkEntity child = chunk(200L, "子块片段");
        child.setParentChunkId(100L);

        lenient().when(chunkMapper.selectBatchIds(anyCollection())).thenReturn(List.of(parent));

        List<RetrievalService.RetrievedChunk> input = List.of(
                new RetrievalService.RetrievedChunk(child, 0.95)
        );

        List<RetrievalService.RetrievedChunk> result = service.expandToParents(input);

        assertEquals(1, result.size());
        assertEquals("父块全文内容，包含完整的上下文信息", result.getFirst().chunk().getChunkText());
        assertEquals(200L, result.getFirst().chunk().getId()); // keeps child's ID
        assertEquals(0.95, result.getFirst().score(), 0.001); // keeps child's score
    }

    @Test
    void shouldReturnUnchangedWhenNoParents() {
        ChunkEntity chunk = chunk(1L, "普通块");

        List<RetrievalService.RetrievedChunk> input = List.of(
                new RetrievalService.RetrievedChunk(chunk, 0.8)
        );

        List<RetrievalService.RetrievedChunk> result = service.expandToParents(input);

        assertEquals(1, result.size());
        assertEquals("普通块", result.getFirst().chunk().getChunkText());
    }

    @Test
    void shouldHandleMissingParentGracefully() {
        ChunkEntity child = chunk(200L, "子块");
        child.setParentChunkId(999L);

        lenient().when(chunkMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        List<RetrievalService.RetrievedChunk> input = List.of(
                new RetrievalService.RetrievedChunk(child, 0.9)
        );

        List<RetrievalService.RetrievedChunk> result = service.expandToParents(input);

        assertEquals(1, result.size());
        assertEquals("子块", result.getFirst().chunk().getChunkText());
    }

    @Test
    void shouldKeepChildScore() {
        ChunkEntity parent = chunk(100L, "父块文本");
        ChunkEntity child = chunk(200L, "子块");
        child.setParentChunkId(100L);

        lenient().when(chunkMapper.selectBatchIds(anyCollection())).thenReturn(List.of(parent));

        List<RetrievalService.RetrievedChunk> input = List.of(
                new RetrievalService.RetrievedChunk(child, 0.42)
        );

        List<RetrievalService.RetrievedChunk> result = service.expandToParents(input);

        assertEquals(0.42, result.getFirst().score(), 0.001);
    }

    @Test
    void shouldHandleMixedChunks() {
        ChunkEntity parent = chunk(100L, "父块全文");
        ChunkEntity childWithParent = chunk(200L, "子块A");
        childWithParent.setParentChunkId(100L);
        ChunkEntity normalChunk = chunk(300L, "普通块");

        lenient().when(chunkMapper.selectBatchIds(anyCollection())).thenReturn(List.of(parent));

        List<RetrievalService.RetrievedChunk> input = List.of(
                new RetrievalService.RetrievedChunk(childWithParent, 0.9),
                new RetrievalService.RetrievedChunk(normalChunk, 0.7)
        );

        List<RetrievalService.RetrievedChunk> result = service.expandToParents(input);

        assertEquals(2, result.size());
        assertEquals("父块全文", result.getFirst().chunk().getChunkText());
        assertEquals("普通块", result.get(1).chunk().getChunkText());
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
