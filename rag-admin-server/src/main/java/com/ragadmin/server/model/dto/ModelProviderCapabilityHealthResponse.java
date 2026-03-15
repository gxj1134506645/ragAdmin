package com.ragadmin.server.model.dto;

public record ModelProviderCapabilityHealthResponse(
        String capabilityType,
        Long modelId,
        String modelCode,
        String status,
        String message
) {
}
