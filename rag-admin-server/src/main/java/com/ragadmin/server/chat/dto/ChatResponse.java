package com.ragadmin.server.chat.dto;

import java.util.List;

public record ChatResponse(
        Long messageId,
        String answer,
        String answerContentType,
        List<ChatReferenceResponse> references,
        ChatUsageResponse usage,
        ChatAnswerMetadataResponse metadata
) {
}
