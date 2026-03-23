package com.ragadmin.server.chat.dto;

import java.util.List;

public record ChatStreamEventResponse(
        String eventType,
        String delta,
        Long messageId,
        String answer,
        String answerContentType,
        List<ChatReferenceResponse> references,
        ChatUsageResponse usage,
        ChatAnswerMetadataResponse metadata,
        String errorMessage
) {

    public static ChatStreamEventResponse delta(String delta) {
        return new ChatStreamEventResponse("DELTA", delta, null, null, null, null, null, null, null);
    }

    public static ChatStreamEventResponse complete(ChatResponse response) {
        return new ChatStreamEventResponse(
                "COMPLETE",
                null,
                response.messageId(),
                response.answer(),
                response.answerContentType(),
                response.references(),
                response.usage(),
                response.metadata(),
                null
        );
    }

    public static ChatStreamEventResponse error(String errorMessage) {
        return new ChatStreamEventResponse("ERROR", null, null, null, null, null, null, null, errorMessage);
    }
}
