package com.ragadmin.server.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record ModelResponse(
        Long id,
        Long providerId,
        String providerCode,
        String providerName,
        String modelCode,
        String modelName,
        List<String> capabilityTypes,
        String modelType,
        Integer maxTokens,
        BigDecimal temperatureDefault,
        String status,
        boolean isDefaultChatModel
) {
}
