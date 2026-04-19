package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Order(20)
public class PdfHeaderFooterCleaner implements DocumentCleanerStep {

    private static final Pattern DATETIME_HEADER_PATTERN = Pattern.compile(
            "^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}(\\s+\\d{1,2}:\\d{2})?.*"
    );
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^\\d+\\s*/\\s*\\d+$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://|www\\.).*", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(DocumentCleanContext context) {
        return context.policy().headerFooterCleanEnabled();
    }

    @Override
    public List<Document> clean(List<Document> documents, DocumentCleanContext context) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        Map<String, Integer> firstLineCount = new HashMap<>();
        Map<String, Integer> lastLineCount = new HashMap<>();
        for (Document document : documents) {
            List<String> lines = normalizedLines(document.getText());
            if (lines.isEmpty()) {
                continue;
            }
            increment(firstLineCount, lines.getFirst());
            increment(lastLineCount, lines.getLast());
        }

        int threshold = Math.max(2, (int) Math.ceil(documents.size() * 0.6));
        List<Document> cleaned = new ArrayList<>();
        for (Document document : documents) {
            List<String> lines = new ArrayList<>(normalizedLines(document.getText()));
            if (lines.isEmpty()) {
                continue;
            }

            if (shouldRemoveHeader(lines, firstLineCount, threshold)) {
                lines.removeFirst();
            }
            if (!lines.isEmpty() && shouldRemoveFooter(lines, lastLineCount, threshold)) {
                lines.removeLast();
            }

            String text = String.join("\n", lines).replaceAll("\n{3,}", "\n\n").trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }

            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put("headerFooterCleaned", Boolean.TRUE);
            cleaned.add(new Document(document.getId(), text, metadata));
        }
        return cleaned;
    }

    private boolean shouldRemoveHeader(List<String> lines, Map<String, Integer> firstLineCount, int threshold) {
        String first = lines.getFirst();
        if (firstLineCount.getOrDefault(first, 0) >= threshold) {
            return true;
        }
        return DATETIME_HEADER_PATTERN.matcher(first).matches() && (first.contains("|") || first.contains(".online"));
    }

    private boolean shouldRemoveFooter(List<String> lines, Map<String, Integer> lastLineCount, int threshold) {
        String last = lines.getLast();
        if (lastLineCount.getOrDefault(last, 0) >= threshold) {
            return true;
        }
        return PAGE_NUMBER_PATTERN.matcher(last).matches() || URL_PATTERN.matcher(last).matches();
    }

    private List<String> normalizedLines(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return text.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void increment(Map<String, Integer> counter, String key) {
        counter.put(key, counter.getOrDefault(key, 0) + 1);
    }
}
