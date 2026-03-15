package com.ragadmin.server.model.dto;

import java.util.List;

public record ModelProviderHealthCheckResponse(
        Long providerId,
        String providerCode,
        String providerName,
        String status,
        String message,
        List<ModelProviderCapabilityHealthResponse> capabilityChecks
) {
}
