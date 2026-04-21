package com.ragadmin.server.document.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LineMergeCleanerTest {

    private final LineMergeCleaner cleaner = new LineMergeCleaner();

    private DocumentCleanContext enabledContext() {
        DocumentCleanPolicy policy = new DocumentCleanPolicy(true, false, true, false, true, false);
        return new DocumentCleanContext(null, policy);
    }

    private DocumentCleanContext disabledContext() {
        return new DocumentCleanContext(null, DocumentCleanPolicy.defaultPolicy());
    }

    @Nested
    class Supports {

        @Test
        void shouldSupportWhenLineMergeEnabled() {
            assertTrue(cleaner.supports(enabledContext()));
        }

        @Test
        void shouldNotSupportWhenLineMergeDisabled() {
            assertFalse(cleaner.supports(disabledContext()));
        }
    }

    @Nested
    class MergeWeakParagraphs {

        @Test
        void shouldMergeShortLinesIntoParagraphs() {
            String weak = "姓名 张三\n年龄 28\n学历 本科\n\n工作经历\n公司A 工程师";
            String merged = cleaner.mergeWeakParagraphs(weak);

            assertTrue(merged.contains("姓名 张三 年龄 28 学历 本科"));
            assertTrue(merged.contains("工作经历 公司A 工程师"));
        }

        @Test
        void shouldRespectDoubleNewlines() {
            String text = "段落一\n\n段落二";
            String merged = cleaner.mergeWeakParagraphs(text);

            assertEquals("段落一\n\n段落二", merged);
        }

        @Test
        void shouldHandleAlreadyMergedText() {
            String text = "已经是完整的段落文本，不需要合并。";
            assertEquals(text, cleaner.mergeWeakParagraphs(text));
        }

        @Test
        void shouldHandleEmptyString() {
            assertEquals("", cleaner.mergeWeakParagraphs(""));
        }
    }

    @Nested
    class Clean {

        @Test
        void shouldMergeWeakParagraphsInDocuments() {
            List<Document> input = List.of(
                    new Document("姓名 张三\n年龄 28\n\n工作经历\n公司A", Map.of())
            );

            List<Document> result = cleaner.clean(input, enabledContext());

            assertEquals(1, result.size());
            assertTrue(result.getFirst().getText().contains("姓名 张三 年龄 28"));
        }

        @Test
        void shouldNotCleanWhenDisabled() {
            assertFalse(cleaner.supports(disabledContext()));
        }

        @Test
        void shouldPreserveMetadata() {
            List<Document> input = List.of(
                    new Document("行一\n行二", Map.of("pageNo", 1))
            );

            List<Document> result = cleaner.clean(input, enabledContext());

            assertEquals(1, result.getFirst().getMetadata().get("pageNo"));
        }
    }
}
