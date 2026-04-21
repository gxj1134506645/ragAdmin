package com.ragadmin.server.document.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OcrNoiseCleanerTest {

    private final OcrNoiseCleaner cleaner = new OcrNoiseCleaner();

    private DocumentCleanContext enabledContext() {
        DocumentCleanPolicy policy = new DocumentCleanPolicy(true, false, true, false, false, true);
        return new DocumentCleanContext(null, policy);
    }

    private DocumentCleanContext disabledContext() {
        return new DocumentCleanContext(null, DocumentCleanPolicy.defaultPolicy());
    }

    @Nested
    class Supports {

        @Test
        void shouldSupportWhenOcrNoiseEnabled() {
            assertTrue(cleaner.supports(enabledContext()));
        }

        @Test
        void shouldNotSupportWhenOcrNoiseDisabled() {
            assertFalse(cleaner.supports(disabledContext()));
        }
    }

    @Nested
    class RemoveOcrNoise {

        @Test
        void shouldRemoveReplacementChar() {
            String input = "Hello\uFFFD World";
            assertEquals("Hello World", cleaner.removeOcrNoise(input));
        }

        @Test
        void shouldRemoveControlChars() {
            String input = "Line1\u0001\u0002Line2\u000BLine3\u001F";
            assertEquals("Line1Line2Line3", cleaner.removeOcrNoise(input));
        }

        @Test
        void shouldPreserveNewlines() {
            String input = "Line1\nLine2\r\nLine3";
            assertEquals("Line1\nLine2\r\nLine3", cleaner.removeOcrNoise(input));
        }

        @Test
        void shouldPreserveNormalText() {
            String input = "正常中文文本，包含标点符号！？";
            assertEquals(input, cleaner.removeOcrNoise(input));
        }
    }

    @Nested
    class Clean {

        @Test
        void shouldCleanOcrNoiseFromDocuments() {
            List<Document> input = List.of(
                    new Document("文本\uFFFD带\u0001噪声", Map.of()),
                    new Document("干净文本", Map.of())
            );

            List<Document> result = cleaner.clean(input, enabledContext());

            assertEquals(2, result.size());
            assertEquals("文本带噪声", result.getFirst().getText());
            assertEquals("干净文本", result.get(1).getText());
        }

        @Test
        void shouldNotCleanWhenDisabled() {
            assertFalse(cleaner.supports(disabledContext()));
        }
    }
}
