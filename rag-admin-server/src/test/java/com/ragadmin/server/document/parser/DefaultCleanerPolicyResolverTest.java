package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCleanerPolicyResolverTest {

    private final DefaultCleanerPolicyResolver resolver = new DefaultCleanerPolicyResolver();

    @Test
    void shouldEnableHeaderFooterWhenPdfTextAndSignalsDetected() {
        DocumentEntity document = document("PDF");
        DocumentSignals signals = new DocumentSignals(true, false, false, false, false, false, false, false, false, false, 0.0, 0.0, 0.1, 0.05);

        DocumentCleanPolicy policy = resolver.resolve(new DocumentCleaningRequest(
                document,
                List.of(new Document("正文", Map.of("parseMode", "TEXT", "readerType", "TIKA"))),
                signals
        ));

        assertTrue(policy.safeCleanEnabled());
        assertTrue(policy.semanticCleanEnabled());
        assertTrue(policy.headerFooterCleanEnabled());
        assertFalse(policy.ocrNoiseCleanEnabled());
    }

    @Test
    void shouldNotEnableHeaderFooterWhenPdfTextButNoSignals() {
        DocumentEntity document = document("PDF");

        DocumentCleanPolicy policy = resolver.resolve(new DocumentCleaningRequest(
                document,
                List.of(new Document("正文", Map.of("parseMode", "TEXT", "readerType", "TIKA"))),
                DocumentSignals.empty()
        ));

        assertFalse(policy.headerFooterCleanEnabled());
        assertFalse(policy.lineMergeEnabled());
    }

    @Test
    void shouldEnableLineMergeWhenWeakParagraphStructure() {
        DocumentEntity document = document("PDF");
        DocumentSignals signals = new DocumentSignals(false, false, false, true, false, false, false, false, false, false, 0.0, 0.0, 0.1, 0.05);

        DocumentCleanPolicy policy = resolver.resolve(new DocumentCleaningRequest(
                document,
                List.of(new Document("正文", Map.of("parseMode", "TEXT", "readerType", "TIKA"))),
                signals
        ));

        assertTrue(policy.lineMergeEnabled());
    }

    @Test
    void shouldPreserveSymbolsForMarkdown() {
        DocumentCleanPolicy markdownPolicy = resolver.resolve(new DocumentCleaningRequest(
                document("MD"),
                List.of(new Document("# 标题\n- 项目", Map.of("parseMode", "TEXT", "readerType", "MARKDOWN"))),
                DocumentSignals.empty()
        ));
        assertTrue(markdownPolicy.preserveSymbols());
        assertFalse(markdownPolicy.semanticCleanEnabled());
    }

    @Test
    void shouldEnableOcrNoiseWhenOcrModeAndNoiseDetected() {
        DocumentSignals signals = new DocumentSignals(false, false, false, false, true, false, false, false, false, false, 0.0, 0.0, 0.1, 0.05);

        DocumentCleanPolicy policy = resolver.resolve(new DocumentCleaningRequest(
                document("PDF"),
                List.of(new Document("扫描内容", Map.of("parseMode", "OCR", "readerType", "TIKA_PDF"))),
                signals
        ));

        assertTrue(policy.ocrNoiseCleanEnabled());
    }

    @Test
    void shouldNotEnableOcrNoiseWhenNoNoiseDetected() {
        DocumentCleanPolicy policy = resolver.resolve(new DocumentCleaningRequest(
                document("PDF"),
                List.of(new Document("扫描内容", Map.of("parseMode", "OCR", "readerType", "TIKA_PDF"))),
                DocumentSignals.empty()
        ));

        assertFalse(policy.ocrNoiseCleanEnabled());
    }

    private DocumentEntity document(String docType) {
        DocumentEntity entity = new DocumentEntity();
        entity.setDocType(docType);
        return entity;
    }
}
