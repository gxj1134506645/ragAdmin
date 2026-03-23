package com.ragadmin.server.model.dto;

public record ModelBatchDeleteFailureResponse(
        Long modelId,
        String modelName,
        String message
) {
}
