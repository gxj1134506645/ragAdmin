package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfDocumentReaderStrategyTest {

    @Test
    void shouldFallbackToPageReaderWhenParagraphReaderReturnsEmpty() throws Exception {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        PdfDocumentReaderStrategy strategy = new PdfDocumentReaderStrategy(new DocumentMetadataFactory(), mineruParseService) {
            @Override
            protected List<Document> readWithParagraphReader(DocumentParseRequest request) {
                return List.of();
            }

            @Override
            protected List<Document> readWithPageReader(DocumentParseRequest request) {
                return List.of(new Document("按页读取文本", Map.of("page_number", 1)));
            }
        };

        List<Document> documents = strategy.read(request());

        assertEquals(1, documents.size());
        assertEquals("按页读取文本", documents.getFirst().getText());
        assertEquals("PDF_PAGE_READER", documents.getFirst().getMetadata().get("readerType"));
        assertEquals("TEXT", documents.getFirst().getMetadata().get("parseMode"));
        assertEquals(1, documents.getFirst().getMetadata().get("page_number"));
    }

    @Test
    void shouldFallbackToPageReaderWhenParagraphReaderThrowsIllegalArgumentException() throws Exception {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        PdfDocumentReaderStrategy strategy = new PdfDocumentReaderStrategy(new DocumentMetadataFactory(), mineruParseService) {
            @Override
            protected List<Document> readWithParagraphReader(DocumentParseRequest request) {
                throw new IllegalArgumentException("Document outline (e.g. TOC) is null");
            }

            @Override
            protected List<Document> readWithPageReader(DocumentParseRequest request) {
                return List.of(new Document("按页回退文本", Map.of("page_number", 1)));
            }
        };

        List<Document> documents = strategy.read(request());

        assertEquals(1, documents.size());
        assertEquals("按页回退文本", documents.getFirst().getText());
        assertEquals("PDF_PAGE_READER", documents.getFirst().getMetadata().get("readerType"));
    }

    @Test
    void shouldFallbackToPageReaderWhenParagraphReaderQualityIsTooLow() throws Exception {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        PdfDocumentReaderStrategy strategy = new PdfDocumentReaderStrategy(new DocumentMetadataFactory(), mineruParseService) {
            @Override
            protected List<Document> readWithParagraphReader(DocumentParseRequest request) {
                return List.of(new Document("过短文本"));
            }

            @Override
            protected List<Document> readWithPageReader(DocumentParseRequest request) {
                return List.of(new Document("这是更完整的按页读取结果，用于覆盖段落抽取质量不足时的降级逻辑。", Map.of("page_number", 1)));
            }
        };

        List<Document> documents = strategy.read(request());

        assertEquals(1, documents.size());
        assertEquals("PDF_PAGE_READER", documents.getFirst().getMetadata().get("readerType"));
        assertEquals(1, documents.getFirst().getMetadata().get("page_number"));
    }

    @Test
    void shouldFallbackToMineruWhenParagraphAndPageReadersReturnEmpty() throws Exception {
        MineruParseService mineruParseService = mock(MineruParseService.class);
        Document mineruDocument = new Document("MinerU PDF 文本");
        mineruDocument.getMetadata().put("page_number", null);
        mineruDocument.getMetadata().put("title", "简历");
        when(mineruParseService.parse(any())).thenReturn(List.of(mineruDocument));
        PdfDocumentReaderStrategy strategy = new PdfDocumentReaderStrategy(new DocumentMetadataFactory(), mineruParseService) {
            @Override
            protected List<Document> readWithParagraphReader(DocumentParseRequest request) {
                return List.of();
            }

            @Override
            protected List<Document> readWithPageReader(DocumentParseRequest request) {
                return List.of();
            }
        };

        List<Document> documents = strategy.read(request());

        assertEquals(1, documents.size());
        assertEquals("MinerU PDF 文本", documents.getFirst().getText());
        assertEquals("MINERU_API", documents.getFirst().getMetadata().get("readerType"));
        assertEquals("OCR", documents.getFirst().getMetadata().get("parseMode"));
        assertEquals("简历", documents.getFirst().getMetadata().get("title"));
    }

    private DocumentParseRequest request() {
        com.ragadmin.server.document.entity.DocumentEntity document = new com.ragadmin.server.document.entity.DocumentEntity();
        document.setDocType("PDF");
        document.setDocName("sample.pdf");
        com.ragadmin.server.document.entity.DocumentVersionEntity version = new com.ragadmin.server.document.entity.DocumentVersionEntity();
        version.setStorageBucket("bucket");
        version.setStorageObjectKey("object");
        return new DocumentParseRequest(document, version, "fake-pdf".getBytes(), "PDF");
    }
}
