package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@Component
public class DocumentContentExtractor {

    private static final List<String> TIKA_SUPPORTED_TYPES = List.of(
            "TXT",
            "MD",
            "MARKDOWN",
            "PDF",
            "DOCX",
            "PPTX",
            "XLSX"
    );

    private static final List<String> OCR_IMAGE_TYPES = List.of(
            "PNG",
            "JPG",
            "JPEG",
            "WEBP"
    );

    private final MinioClientFactory minioClientFactory;
    private final TesseractOcrService tesseractOcrService;
    private final Tika tika = new Tika();

    public DocumentContentExtractor(MinioClientFactory minioClientFactory, TesseractOcrService tesseractOcrService) {
        this.minioClientFactory = minioClientFactory;
        this.tesseractOcrService = tesseractOcrService;
    }

    public String extract(DocumentEntity document, DocumentVersionEntity version) throws Exception {
        String docType = normalizeDocType(document.getDocType());
        byte[] content = loadContent(version);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容为空，无法解析");
        }
        if (OCR_IMAGE_TYPES.contains(docType)) {
            return extractImageByOcr(content, docType);
        }
        if (!TIKA_SUPPORTED_TYPES.contains(docType)) {
            throw new IllegalArgumentException("当前仅支持 TXT/MD/PDF/DOCX/PPTX/XLSX 以及 PNG/JPG/JPEG/WEBP 文档解析");
        }
        return extractByTikaOrOcrFallback(content, docType);
    }

    protected byte[] loadContent(DocumentVersionEntity version) throws Exception {
        MinioClient minioClient = minioClientFactory.createClient();
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(version.getStorageBucket())
                .object(version.getStorageObjectKey())
                .build())) {
            return inputStream.readAllBytes();
        }
    }

    protected String extractByTikaOrOcrFallback(byte[] content, String docType) throws Exception {
        String parsed;
        try (InputStream tikaInputStream = new ByteArrayInputStream(content)) {
            parsed = tika.parseToString(tikaInputStream);
        }
        if (!"PDF".equals(docType) || StringUtils.hasText(parsed) || !tesseractOcrService.isEnabled()) {
            return parsed;
        }
        try (InputStream pdfInputStream = new ByteArrayInputStream(content)) {
            return tesseractOcrService.extractPdfText(pdfInputStream);
        }
    }

    protected String extractImageByOcr(byte[] content, String docType) throws Exception {
        if (!tesseractOcrService.isEnabled()) {
            throw new IllegalArgumentException("当前图片解析依赖 OCR，需先启用 Tesseract OCR");
        }
        try (InputStream imageInputStream = new ByteArrayInputStream(content)) {
            return tesseractOcrService.extractImageText(imageInputStream, docType.toLowerCase(Locale.ROOT));
        }
    }

    private String normalizeDocType(String docType) {
        return docType == null ? "" : docType.toUpperCase(Locale.ROOT);
    }
}
