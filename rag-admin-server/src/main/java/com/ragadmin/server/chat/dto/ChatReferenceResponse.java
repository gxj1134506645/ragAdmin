package com.ragadmin.server.chat.dto;

public record ChatReferenceResponse(
        Long documentId,
        String documentName,
        Long chunkId,
        Integer chunkNo,
        double score,
        String contentSnippet
) {
}
