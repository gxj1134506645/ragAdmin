package com.ragadmin.server.document.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.document.entity.ChunkVectorRefEntity;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.infra.vector.MilvusVectorStoreClient;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.model.service.ModelService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ChunkVectorizationService {

    private final ModelService modelService;
    private final EmbeddingClientRegistry embeddingClientRegistry;
    private final MilvusVectorStoreClient milvusVectorStoreClient;
    private final ChunkVectorRefMapper chunkVectorRefMapper;

    public ChunkVectorizationService(
            ModelService modelService,
            EmbeddingClientRegistry embeddingClientRegistry,
            MilvusVectorStoreClient milvusVectorStoreClient,
            ChunkVectorRefMapper chunkVectorRefMapper
    ) {
        this.modelService = modelService;
        this.embeddingClientRegistry = embeddingClientRegistry;
        this.milvusVectorStoreClient = milvusVectorStoreClient;
        this.chunkVectorRefMapper = chunkVectorRefMapper;
    }

    @Transactional
    public void vectorize(KnowledgeBaseEntity knowledgeBase, List<ChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        EmbeddingModelDescriptor descriptor = modelService.resolveEmbeddingModelDescriptor(knowledgeBase.getEmbeddingModelId());
        EmbeddingModelClient client = embeddingClientRegistry.getClient(descriptor.providerCode());
        List<List<Float>> embeddings = client.embed(descriptor.modelCode(), chunks.stream().map(ChunkEntity::getChunkText).toList());
        if (embeddings.size() != chunks.size()) {
            throw new BusinessException("EMBEDDING_SIZE_MISMATCH", "Embedding 返回数量与 chunk 数量不一致", HttpStatus.BAD_GATEWAY);
        }

        int dimension = embeddings.getFirst().size();
        String collectionName = buildCollectionName(knowledgeBase.getId(), descriptor.modelId(), dimension);
        milvusVectorStoreClient.ensureCollection(collectionName, dimension);
        milvusVectorStoreClient.upsert(collectionName, chunks, embeddings);

        for (int index = 0; index < chunks.size(); index++) {
            ChunkEntity chunk = chunks.get(index);
            ChunkVectorRefEntity ref = new ChunkVectorRefEntity();
            ref.setKbId(chunk.getKbId());
            ref.setChunkId(chunk.getId());
            ref.setEmbeddingModelId(descriptor.modelId());
            ref.setCollectionName(collectionName);
            ref.setPartitionName(null);
            ref.setVectorId(buildVectorId(chunk));
            ref.setEmbeddingDim(dimension);
            ref.setStatus("ENABLED");
            chunkVectorRefMapper.insert(ref);
        }
    }

    @Transactional
    public void deleteRefsByChunkIds(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        chunkVectorRefMapper.delete(new LambdaQueryWrapper<ChunkVectorRefEntity>()
                .in(ChunkVectorRefEntity::getChunkId, chunkIds));
    }

    public String buildVectorId(ChunkEntity chunk) {
        return "dv_" + chunk.getDocumentVersionId() + "_chunk_" + chunk.getChunkNo();
    }

    private String buildCollectionName(Long kbId, Long modelId, int dimension) {
        return "kb_" + kbId + "_emb_" + modelId + "_d_" + dimension;
    }
}
