package com.ragadmin.server.statistics.dto;

public record KnowledgeBaseChatStatisticsResponse(
        Long kbId,
        Long sessionCount,
        Long messageCount,
        Long distinctUserCount,
        Long totalPromptTokens,
        Long totalCompletionTokens,
        Long averageLatencyMs
) {
}
