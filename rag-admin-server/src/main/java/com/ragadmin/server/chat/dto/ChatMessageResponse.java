package com.ragadmin.server.chat.dto;

import java.util.List;

public record ChatMessageResponse(
        Long messageId,
        String question,
        String answer,
        String answerContentType,
        List<ChatReferenceResponse> references,
        ChatAnswerMetadataResponse metadata,
        String feedbackType,
        String feedbackComment
) {
}
