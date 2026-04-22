package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ParentChunkExpansionService {

    @Autowired
    private ChunkMapper chunkMapper;

    public List<RetrievalService.RetrievedChunk> expandToParents(List<RetrievalService.RetrievedChunk> childChunks) {
        Set<Long> parentIds = childChunks.stream()
                .map(c -> c.chunk().getParentChunkId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (parentIds.isEmpty()) {
            return childChunks;
        }

        Map<Long, ChunkEntity> parentMap = chunkMapper.selectBatchIds(parentIds).stream()
                .collect(Collectors.toMap(ChunkEntity::getId, Function.identity()));

        return childChunks.stream()
                .map(chunk -> {
                    Long parentId = chunk.chunk().getParentChunkId();
                    if (parentId == null) {
                        return chunk;
                    }
                    ChunkEntity parent = parentMap.get(parentId);
                    if (parent == null || parent.getChunkText() == null) {
                        return chunk;
                    }
                    ChunkEntity expanded = copyWithText(chunk.chunk(), parent.getChunkText());
                    return new RetrievalService.RetrievedChunk(expanded, chunk.score());
                })
                .toList();
    }

    private ChunkEntity copyWithText(ChunkEntity source, String newText) {
        ChunkEntity copy = new ChunkEntity();
        copy.setId(source.getId());
        copy.setKbId(source.getKbId());
        copy.setDocumentId(source.getDocumentId());
        copy.setDocumentVersionId(source.getDocumentVersionId());
        copy.setChunkNo(source.getChunkNo());
        copy.setChunkText(newText);
        copy.setTokenCount(source.getTokenCount());
        copy.setCharCount(newText.length());
        copy.setMetadataJson(source.getMetadataJson());
        copy.setEnabled(source.getEnabled());
        copy.setParentChunkId(source.getParentChunkId());
        copy.setChunkStrategy(source.getChunkStrategy());
        return copy;
    }
}
