package com.ragadmin.server.retrieval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.ChunkVectorRefEntity;
import com.ragadmin.server.document.mapper.ChunkMapper;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.infra.vector.MilvusVectorStoreClient;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.model.service.ModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RetrievalService {

    @Autowired
    private ModelService modelService;

    @Autowired
    private EmbeddingClientRegistry embeddingClientRegistry;

    @Autowired
    private MilvusVectorStoreClient milvusVectorStoreClient;

    @Autowired
    private ChunkVectorRefMapper chunkVectorRefMapper;

    @Autowired
    private ChunkMapper chunkMapper;

    public RetrievalResult retrieve(KnowledgeBaseEntity knowledgeBase, String question) {
        ChunkVectorRefEntity sampleRef = chunkVectorRefMapper.selectOne(new LambdaQueryWrapper<ChunkVectorRefEntity>()
                .eq(ChunkVectorRefEntity::getKbId, knowledgeBase.getId())
                .eq(ChunkVectorRefEntity::getEmbeddingModelId, knowledgeBase.getEmbeddingModelId())
                .last("LIMIT 1"));
        if (sampleRef == null) {
            return new RetrievalResult(List.of(), "");
        }

        var embeddingDescriptor = modelService.resolveEmbeddingModelDescriptor(knowledgeBase.getEmbeddingModelId());
        EmbeddingModelClient client = embeddingClientRegistry.getClient(embeddingDescriptor.providerCode());
        List<Float> vector = client.embed(embeddingDescriptor.modelCode(), List.of(question)).getFirst();
        List<MilvusVectorStoreClient.SearchResult> searchResults = milvusVectorStoreClient.search(
                sampleRef.getCollectionName(),
                vector,
                knowledgeBase.getRetrieveTopK()
        );
        if (searchResults.isEmpty()) {
            return new RetrievalResult(List.of(), "");
        }

        List<ChunkVectorRefEntity> refs = chunkVectorRefMapper.selectByVectorIds(searchResults.stream()
                .map(MilvusVectorStoreClient.SearchResult::vectorId)
                .toList());
        Map<String, ChunkVectorRefEntity> refMap = refs.stream()
                .collect(Collectors.toMap(ChunkVectorRefEntity::getVectorId, Function.identity()));
        Map<Long, ChunkEntity> chunkMap = chunkMapper.selectBatchIds(refs.stream()
                        .map(ChunkVectorRefEntity::getChunkId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(ChunkEntity::getId, Function.identity()));

        List<RetrievedChunk> chunks = searchResults.stream()
                .map(result -> {
                    ChunkVectorRefEntity ref = refMap.get(result.vectorId());
                    if (ref == null) {
                        return null;
                    }
                    ChunkEntity chunk = chunkMap.get(ref.getChunkId());
                    if (chunk == null) {
                        return null;
                    }
                    return new RetrievedChunk(chunk, result.score());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(RetrievedChunk::score).reversed())
                .toList();

        String context = chunks.stream()
                .map(item -> "片段" + item.chunk().getChunkNo() + ":\n" + item.chunk().getChunkText())
                .collect(Collectors.joining("\n\n"));
        return new RetrievalResult(chunks, context);
    }

    public List<ChatReferenceResponse> toReferenceResponses(List<RetrievedChunk> chunks, java.util.function.Function<Long, String> documentNameResolver) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        return chunks.stream()
                .map(item -> new ChatReferenceResponse(
                        item.chunk().getDocumentId(),
                        documentNameResolver.apply(item.chunk().getDocumentId()),
                        item.chunk().getId(),
                        item.score(),
                        snippet(item.chunk().getChunkText())
                ))
                .toList();
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 160 ? text : text.substring(0, 160);
    }

    public record RetrievalResult(List<RetrievedChunk> chunks, String context) {
    }

    public record RetrievedChunk(ChunkEntity chunk, double score) {
    }
}
