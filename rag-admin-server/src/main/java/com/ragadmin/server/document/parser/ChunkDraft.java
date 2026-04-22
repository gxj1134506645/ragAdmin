package com.ragadmin.server.document.parser;

import java.util.Map;

public record ChunkDraft(String text, Map<String, Object> metadata, Long parentChunkId) {

    public ChunkDraft(String text, Map<String, Object> metadata) {
        this(text, metadata, null);
    }

    public boolean isChild() {
        return parentChunkId != null;
    }
}
