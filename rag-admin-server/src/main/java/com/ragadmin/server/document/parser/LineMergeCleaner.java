package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(25)
public class LineMergeCleaner implements DocumentCleanerStep {

    @Override
    public boolean supports(DocumentCleanContext context) {
        return context.policy().lineMergeEnabled();
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
            String merged = mergeWeakParagraphs(text);
            if (merged.equals(text)) {
                result.add(document);
            } else {
                result.add(new Document(document.getId(), merged, document.getMetadata()));
            }
        }
        return result;
    }

    String mergeWeakParagraphs(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder(text.length());
        StringBuilder paragraph = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (paragraph.length() > 0) {
                    if (result.length() > 0) {
                        result.append("\n\n");
                    }
                    result.append(paragraph);
                    paragraph = new StringBuilder();
                }
                if (!line.isEmpty()) {
                    // original had content but trimmed to empty (whitespace-only line)
                    // treat as paragraph break
                }
            } else {
                if (paragraph.length() > 0) {
                    paragraph.append(" ");
                }
                paragraph.append(trimmed);
            }
        }

        if (paragraph.length() > 0) {
            if (result.length() > 0) {
                result.append("\n\n");
            }
            result.append(paragraph);
        }

        return result.toString();
    }
}
