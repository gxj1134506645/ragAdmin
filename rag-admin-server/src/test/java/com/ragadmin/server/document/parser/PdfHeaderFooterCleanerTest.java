package com.ragadmin.server.document.parser;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PdfHeaderFooterCleanerTest {

    private final PdfHeaderFooterCleaner cleaner = new PdfHeaderFooterCleaner();

    @Test
    void shouldRemoveRepeatedHeaderAndFooterAcrossPages() {
        List<Document> cleaned = cleaner.clean(List.of(
                new Document("简历 | gfish.online\n第一页正文\n1/2"),
                new Document("简历 | gfish.online\n第二页正文\n1/2")
        ), new DocumentCleanContext(document(), new DocumentCleanPolicy(true, true, true, true, true, false)));

        assertEquals(2, cleaned.size());
        assertEquals("第一页正文", cleaned.getFirst().getText());
        assertEquals("第二页正文", cleaned.get(1).getText());
        assertEquals(Boolean.TRUE, cleaned.getFirst().getMetadata().get("headerFooterCleaned"));
    }

    @Test
    void shouldRemoveSinglePageDatetimeStyleHeader() {
        List<Document> cleaned = cleaner.clean(List.of(
                new Document("2026/4/12 12:42 简历 | gfsh.online\n葛孝杰\n电话：18852966590")
        ), new DocumentCleanContext(document(), new DocumentCleanPolicy(true, true, true, true, true, false)));

        assertEquals(1, cleaned.size());
        assertFalse(cleaned.getFirst().getText().contains("2026/4/12 12:42"));
        assertEquals("葛孝杰\n电话：18852966590", cleaned.getFirst().getText());
    }

    private com.ragadmin.server.document.entity.DocumentEntity document() {
        com.ragadmin.server.document.entity.DocumentEntity entity = new com.ragadmin.server.document.entity.DocumentEntity();
        entity.setDocType("PDF");
        return entity;
    }
}
