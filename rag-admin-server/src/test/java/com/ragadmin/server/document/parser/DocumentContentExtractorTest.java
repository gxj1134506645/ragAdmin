package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentContentExtractorTest {

    @Mock
    private MinioClientFactory minioClientFactory;

    @Mock
    private TesseractOcrService tesseractOcrService;

    @Test
    void shouldUseOcrForImageDocument() throws Exception {
        DocumentContentExtractor extractor = new TestableDocumentContentExtractor(minioClientFactory, tesseractOcrService, "fake-image".getBytes());
        when(tesseractOcrService.isEnabled()).thenReturn(true);
        when(tesseractOcrService.extractImageText(any(InputStream.class), org.mockito.ArgumentMatchers.eq("png")))
                .thenReturn("图片 OCR 文本");

        String content = extractor.extract(document("PNG"), version());

        assertEquals("图片 OCR 文本", content);
    }

    @Test
    void shouldFallbackToPdfOcrWhenTikaReturnsBlank() throws Exception {
        DocumentContentExtractor extractor = new TestableDocumentContentExtractor(minioClientFactory, tesseractOcrService, "fake-pdf".getBytes()) {
            @Override
            protected String extractByTikaOrOcrFallback(byte[] content, String docType) {
                return "扫描 PDF OCR 文本";
            }
        };

        String content = extractor.extract(document("PDF"), version());

        assertEquals("扫描 PDF OCR 文本", content);
    }

    @Test
    void shouldRejectImageDocumentWhenOcrDisabled() throws Exception {
        DocumentContentExtractor extractor = new TestableDocumentContentExtractor(minioClientFactory, tesseractOcrService, "fake-image".getBytes());
        when(tesseractOcrService.isEnabled()).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(document("PNG"), version())
        );

        assertEquals("当前图片解析依赖 OCR，需先启用 Tesseract OCR", exception.getMessage());
        verify(tesseractOcrService, never()).extractImageText(any(InputStream.class), org.mockito.ArgumentMatchers.anyString());
    }

    private DocumentEntity document(String docType) {
        DocumentEntity entity = new DocumentEntity();
        entity.setDocType(docType);
        return entity;
    }

    private DocumentVersionEntity version() {
        DocumentVersionEntity entity = new DocumentVersionEntity();
        entity.setStorageBucket("bucket");
        entity.setStorageObjectKey("object");
        return entity;
    }

    private static class TestableDocumentContentExtractor extends DocumentContentExtractor {

        private final byte[] content;

        private TestableDocumentContentExtractor(MinioClientFactory minioClientFactory, TesseractOcrService tesseractOcrService, byte[] content) {
            super(minioClientFactory, tesseractOcrService);
            this.content = content;
        }

        @Override
        protected byte[] loadContent(DocumentVersionEntity version) {
            return content;
        }
    }
}
