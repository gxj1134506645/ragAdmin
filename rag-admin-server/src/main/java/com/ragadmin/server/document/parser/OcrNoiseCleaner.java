package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(15)
public class OcrNoiseCleaner implements DocumentCleanerStep {

    @Override
    public boolean supports(DocumentCleanContext context) {
        return context.policy().ocrNoiseCleanEnabled();
    }

    @Override
    public List<Document> clean(List<Document> documents, DocumentCleanContext context) {
        List<Document> result = new ArrayList<>(documents.size());
        for (Document document : documents) {
            String text = document.getText();
            if (text == null || text.isEmpty()) {
                result.add(document);
                continue;
            }
            String cleaned = removeOcrNoise(text);
            if (cleaned.equals(text)) {
                result.add(document);
            } else {
                result.add(new Document(document.getId(), cleaned, document.getMetadata()));
            }
        }
        return result;
    }

    String removeOcrNoise(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\uFFFD') {
                continue;
            }
            if (c <= '\u0008' || c == '\u000B' || (c >= '\u000E' && c <= '\u001F')) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
