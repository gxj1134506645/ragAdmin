package com.ragadmin.server.statistics.dto;

public record ModelCallStatisticsResponse(
        Long modelId,
        String modelCode,
        String modelName,
        Long callCount,
        Long totalPromptTokens,
        Long totalCompletionTokens,
        Long averageLatencyMs
) {
}
