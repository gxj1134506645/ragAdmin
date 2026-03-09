package com.ragadmin.server.document.parser;

import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.entity.DocumentVersionEntity;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@Component
public class DocumentContentExtractor {

    private static final List<String> SUPPORTED_TYPES = List.of(
            "TXT",
            "MD",
            "MARKDOWN",
            "PDF",
            "DOCX",
            "PPTX",
            "XLSX"
    );

    private final MinioClientFactory minioClientFactory;
    private final Tika tika = new Tika();

    public DocumentContentExtractor(MinioClientFactory minioClientFactory) {
        this.minioClientFactory = minioClientFactory;
    }

    public String extract(DocumentEntity document, DocumentVersionEntity version) throws Exception {
        String docType = document.getDocType() == null ? "" : document.getDocType().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(docType)) {
            throw new IllegalArgumentException("当前仅支持 TXT/MD/PDF/DOCX/PPTX/XLSX 文档解析");
        }

        MinioClient minioClient = minioClientFactory.createClient();
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(version.getStorageBucket())
                .object(version.getStorageObjectKey())
                .build())) {
            return tika.parseToString(inputStream);
        }
    }
}
