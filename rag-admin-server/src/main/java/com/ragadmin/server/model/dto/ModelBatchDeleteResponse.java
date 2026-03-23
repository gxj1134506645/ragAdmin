package com.ragadmin.server.model.dto;

import java.util.List;

public record ModelBatchDeleteResponse(
        int requestedCount,
        int successCount,
        int failedCount,
        List<Long> deletedIds,
        List<ModelBatchDeleteFailureResponse> failedItems
) {
}
