package com.ragadmin.server.document.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Order(40)
public class PdfDocumentReaderStrategy implements DocumentReaderStrategy {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentReaderStrategy.class);
    private static final int MIN_PARAGRAPH_TOTAL_CHARS = 120;
    private static final int MIN_PARAGRAPH_DOC_CHARS = 40;

    private final DocumentMetadataFactory documentMetadataFactory;
    private final MineruParseService mineruParseService;

    public PdfDocumentReaderStrategy(DocumentMetadataFactory documentMetadataFactory, MineruParseService mineruParseService) {
        this.documentMetadataFactory = documentMetadataFactory;
        this.mineruParseService = mineruParseService;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return "PDF".equals(request.docType());
    }

    @Override
    public List<Document> read(DocumentParseRequest request) {
        List<Document> paragraphDocuments = tryReadWithParagraphReader(request);
        if (hasUsableParagraphDocuments(paragraphDocuments, request)) {
            return documentMetadataFactory.enrichDocuments(paragraphDocuments, request, "PDF_PARAGRAPH_READER", "TEXT");
        }
        List<Document> pageDocuments = readWithPageReader(request);
        if (hasTextDocuments(pageDocuments)) {
            return documentMetadataFactory.enrichDocuments(pageDocuments, request, "PDF_PAGE_READER", "TEXT");
        }
        try {
            return documentMetadataFactory.enrichDocuments(mineruParseService.parse(request), request, "MINERU_API", "OCR");
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 解析失败，按页读取和 MinerU 均未成功", ex);
        }
    }

    private List<Document> tryReadWithParagraphReader(DocumentParseRequest request) {
        try {
            return readWithParagraphReader(request);
        } catch (IllegalArgumentException ex) {
            log.info("PDF 段落读取降级为按页读取，documentName={}, reason={}", request.document().getDocName(), ex.getMessage());
            return List.of();
        } catch (RuntimeException ex) {
            log.warn("PDF 段落读取异常，继续尝试按页读取，documentName={}, reason={}", request.document().getDocName(), ex.getMessage());
            return List.of();
        }
    }

    protected List<Document> readWithParagraphReader(DocumentParseRequest request) {
        ParagraphPdfDocumentReader reader = new ParagraphPdfDocumentReader(new ByteArrayResource(request.content(), request.document().getDocName()));
        return reader.get();
    }

    protected List<Document> readWithPageReader(DocumentParseRequest request) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(new ByteArrayResource(request.content(), request.document().getDocName()));
        return reader.get();
    }

    private boolean hasTextDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return false;
        }
        return documents.stream().anyMatch(document -> StringUtils.hasText(document.getText()));
    }

    private boolean hasUsableParagraphDocuments(List<Document> documents, DocumentParseRequest request) {
        if (!hasTextDocuments(documents)) {
            return false;
        }
        int totalChars = documents.stream()
                .map(Document::getText)
                .filter(StringUtils::hasText)
                .mapToInt(String::length)
                .sum();
        int maxChars = documents.stream()
                .map(Document::getText)
                .filter(StringUtils::hasText)
                .mapToInt(String::length)
                .max()
                .orElse(0);
        if (totalChars < MIN_PARAGRAPH_TOTAL_CHARS || maxChars < MIN_PARAGRAPH_DOC_CHARS) {
            log.info("PDF 段落读取结果偏弱，降级为按页读取，documentName={}, paragraphDocCount={}, totalChars={}, maxChars={}",
                    request.document().getDocName(), documents.size(), totalChars, maxChars);
            return false;
        }
        return true;
    }
}
