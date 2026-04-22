package com.ragadmin.server.knowledge.dto;

public record KnowledgeBaseResponse(
        Long id,
        String kbCode,
        String kbName,
        String description,
        Long embeddingModelId,
        String embeddingModelName,
        Integer retrieveTopK,
        Boolean rerankEnabled,
        String retrievalMode,
        String retrievalQueryRewritingMode,
        String status
) {
}
