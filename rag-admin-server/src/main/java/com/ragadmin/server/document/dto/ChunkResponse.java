package com.ragadmin.server.document.dto;

public record ChunkResponse(
        Long id,
        Integer chunkNo,
        String chunkText,
        Integer tokenCount,
        Integer charCount,
        Boolean enabled
) {
}
