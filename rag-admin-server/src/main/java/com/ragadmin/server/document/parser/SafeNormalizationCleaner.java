package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(10)
public class SafeNormalizationCleaner implements DocumentCleanerStep {

    private static final String CLEAN_VERSION = "v1";

    @Override
    public boolean supports(DocumentCleanContext context) {
        return context.policy().safeCleanEnabled();
    }

    @Override
    public List<Document> clean(List<Document> documents, DocumentCleanContext context) {
        List<Document> cleanedDocuments = new ArrayList<>();
        for (Document document : documents) {
            if (document == null || !document.isText()) {
                continue;
            }
            String cleanedText = cleanText(document.getText());
            if (!StringUtils.hasText(cleanedText)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put("cleaned", Boolean.TRUE);
            metadata.put("cleanVersion", CLEAN_VERSION);
            cleanedDocuments.add(new Document(document.getId(), cleanedText, metadata));
        }
        return cleanedDocuments;
    }

    private String cleanText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalizedLineBreaks = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalizedLineBreaks.split("\n", -1);
        List<String> normalizedLines = new ArrayList<>();
        for (String line : lines) {
            normalizedLines.add(line.strip());
        }
        return String.join("\n", normalizedLines)
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
