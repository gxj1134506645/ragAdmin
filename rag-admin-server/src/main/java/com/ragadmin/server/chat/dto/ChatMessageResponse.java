package com.ragadmin.server.chat.dto;

import java.util.List;

public record ChatMessageResponse(
        Long messageId,
        String question,
        String answer,
        List<ChatReferenceResponse> references,
        String feedbackType,
        String feedbackComment
) {
}
