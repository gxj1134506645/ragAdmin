package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownChunkStrategyTest {

    private final MarkdownChunkStrategy strategy = new MarkdownChunkStrategy();

    private ChunkContext mdContext(int maxChunkChars, int overlapChars) {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        DocumentSignals signals = new DocumentSignals(false, false, false, false, false, false, false,
                false, false, true, 0.0, 0.0, 0.1, 0.05);
        return ChunkContext.of(doc, signals, new ChunkStrategyProperties(maxChunkChars, overlapChars, 50), "TEXT");
    }

    private ChunkContext noHeadingContext() {
        DocumentEntity doc = new DocumentEntity();
        doc.setDocType("PDF");
        return ChunkContext.of(doc, DocumentSignals.empty(), ChunkStrategyProperties.defaults(), "TEXT");
    }

    @Nested
    class Supports {

        @Test
        void shouldMatchWhenHeadingStructureDetected() {
            assertTrue(strategy.supports(mdContext(800, 120)));
        }

        @Test
        void shouldNotMatchWhenNoHeadingStructure() {
            assertFalse(strategy.supports(noHeadingContext()));
        }
    }

    @Nested
    class SplitByHeadings {

        @Test
        void shouldSplitAtH1AndH2() {
            String md = "# 标题一\n\n段落一内容\n\n## 标题二\n\n段落二内容";
            List<String> sections = strategy.splitByHeadings(md);

            assertEquals(2, sections.size());
            assertTrue(sections.getFirst().startsWith("# 标题一"));
            assertTrue(sections.get(1).startsWith("## 标题二"));
        }

        @Test
        void shouldNotSplitInsideCodeBlock() {
            String md = "# 示例\n\n```\n# 这是代码里的注释\nnot a heading\n```\n\n## 下一个标题";
            List<String> sections = strategy.splitByHeadings(md);

            assertEquals(2, sections.size());
            assertTrue(sections.getFirst().contains("# 这是代码里的注释"));
            assertTrue(sections.get(1).startsWith("## 下一个标题"));
        }

        @Test
        void shouldTreatNoHeadingAsSingleSection() {
            String md = "没有标题的纯文本\n\n第二段";
            List<String> sections = strategy.splitByHeadings(md);

            assertEquals(1, sections.size());
        }
    }

    @Nested
    class Chunk {

        @Test
        void shouldPreserveMetadata() {
            String md = "# 标题\n\n" + "a".repeat(300) + "\n\n## 标题二\n\n" + "b".repeat(300);
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(md, Map.of("readerType", "MARKDOWN_READER"))),
                    mdContext(400, 80));

            assertFalse(chunks.isEmpty());
            assertEquals("MARKDOWN_READER", chunks.getFirst().metadata().get("readerType"));
            assertNotNull(chunks.getFirst().metadata().get("parentDocumentId"));
        }

        @Test
        void shouldKeepCodeBlockInSingleChunk() {
            String codeBlock = "```\n" + String.join("\n", java.util.Collections.nCopies(20, "print('hello world')")) + "\n```";
            String md = "# 代码示例\n\n" + codeBlock + "\n\n## 说明\n\n代码说明文字";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(md, Map.of())),
                    mdContext(200, 40));

            boolean codeBlockIntact = chunks.stream()
                    .anyMatch(c -> c.text().contains("```") && c.text().indexOf("```") != c.text().lastIndexOf("```"));
            assertTrue(codeBlockIntact, "代码块应完整保留在同一个切片中");
        }

        @Test
        void shouldAggregateSmallSections() {
            String md = "# A\n\n短内容A\n\n# B\n\n短内容B\n\n# C\n\n短内容C";
            List<ChunkDraft> chunks = strategy.chunk(
                    List.of(new Document(md, Map.of())),
                    mdContext(800, 120));

            assertEquals(1, chunks.size(), "短标题段落应聚合成一个切片");
            assertTrue(chunks.getFirst().text().contains("# A"));
            assertTrue(chunks.getFirst().text().contains("# C"));
        }
    }
}
