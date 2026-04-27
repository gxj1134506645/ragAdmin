package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;

import java.util.Set;

public record ChunkContext(
        DocumentEntity document,
        DocumentSignals signals,
        ChunkStrategyProperties properties,
        String parseMode,
        String contentType
) {

    private static final Set<String> MD_TYPES = Set.of("MD", "MARKDOWN");
    private static final Set<String> HTML_TYPES = Set.of("HTML", "HTM");

    public boolean isDocType(String type) {
        return document != null && type != null && type.equalsIgnoreCase(document.getDocType());
    }

    public boolean isDocTypeIn(Set<String> types) {
        return document != null && types.contains(document.getDocType().toUpperCase());
    }

    public boolean isMarkdown() {
        return isDocTypeIn(MD_TYPES);
    }

    public boolean isHtml() {
        return isDocTypeIn(HTML_TYPES);
    }

    public boolean isPdf() {
        return isDocType("PDF");
    }

    public boolean isOcrMode() {
        return "OCR".equals(parseMode);
    }

    public boolean isTextMode() {
        return parseMode == null || "TEXT".equals(parseMode);
    }

    public boolean contentContainsTable() {
        return signals != null && signals.containsTable();
    }

    public boolean contentContainsImage() {
        return signals != null && signals.containsImage();
    }

    public boolean hasHeadingStructure() {
        return signals != null && signals.markdownHeadingDetected();
    }

    public static ChunkContext of(DocumentEntity document, DocumentSignals signals,
                                   ChunkStrategyProperties properties, String parseMode) {
        String contentType = signals != null ? signals.inferContentType() : "TEXT";
        return new ChunkContext(document, signals, properties, parseMode, contentType);
    }
}
