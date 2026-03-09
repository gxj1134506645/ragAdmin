package com.ragadmin.server.knowledge.dto;

public record KnowledgeBaseResponse(
        Long id,
        String kbCode,
        String kbName,
        String description,
        Long embeddingModelId,
        String embeddingModelName,
        Long chatModelId,
        String chatModelName,
        Integer retrieveTopK,
        Boolean rerankEnabled,
        String status
) {
}
