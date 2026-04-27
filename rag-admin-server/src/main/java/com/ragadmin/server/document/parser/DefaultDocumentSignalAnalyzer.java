package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class DefaultDocumentSignalAnalyzer implements DocumentSignalAnalyzer {

    private final SignalAnalysisProperties properties;

    public DefaultDocumentSignalAnalyzer(SignalAnalysisProperties properties) {
        this.properties = properties;
    }

    private static final Pattern OCR_NOISE_PATTERN = Pattern.compile(
            "[" +
                    "\\uFFFD" +
                    "\\u0000-\\u0008\\u000B\\u000E-\\u001F" +
                    "]" // replacement char and control chars
    );
    private static final Pattern SPECIAL_SYMBOL_PATTERN = Pattern.compile(
            "[•●◆◇■□★☆►◁▶◀▲▼§¶†‡※◎ΘΔΩ∑∏√∞≡≠≤≥±×÷≈∝∈∉⊆⊇∪∩∧∨¬⊕⊗ℵ]"
    );
    private static final Pattern DATETIME_HEADER_PATTERN = Pattern.compile(
            "^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}(\\s+\\d{1,2}:\\d{2})?.*"
    );
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^\\d+\\s*/\\s*\\d+$");

    private static final Pattern TOC_PATTERN = Pattern.compile(
            "^\\s*(目[\\s　]*录|Contents?|目录|Table\\s+of\\s+Contents?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOC_ENTRY_PATTERN = Pattern.compile(
            "^\\s*(第[一二三四五六七八九十百千]+[章节篇部]|[\\d]+[\\.、]\\s*\\S)" +
                    "|.*\\.{" + 3 + ",}\\s*\\d+\\s*$"  // dotted TOC entries like "Chapter 1 ....... 12"
    );
    private static final Pattern CHAPTER_HEADING_PATTERN = Pattern.compile(
            "^\\s*(第[一二三四五六七八九十百千]+[章节篇部]|Chapter\\s+\\d+|\\d+[\\.\\s]\\s*\\S)"
    );

    @Override
    public DocumentSignals analyze(List<Document> documents, DocumentCleanContext context) {
        if (documents == null || documents.isEmpty()) {
            return DocumentSignals.empty();
        }

        return new DocumentSignals(
                detectRepeatedHeader(documents),
                detectRepeatedFooter(documents),
                detectTooManyBlankLines(documents),
                detectWeakParagraphStructure(documents),
                detectOcrNoise(documents),
                detectSymbolDensityHigh(documents),
                detectTocOutlineMissing(documents),
                detectMarkdownTable(documents),
                detectMarkdownImage(documents),
                detectMarkdownHeading(documents),
                computeTableRatio(documents),
                computeImageRatio(documents),
                properties.getTableRatioThreshold(),
                properties.getImageRatioThreshold()
        );
    }

    boolean detectRepeatedHeader(List<Document> documents) {
        if (documents.size() < 2) {
            return false;
        }
        if (documents.size() >= 3) {
            Map<String, Integer> firstLineCount = countFirstOrLastLines(documents, true);
            int threshold = Math.max(2, (int) Math.ceil(documents.size() * properties.getHeaderFooterThreshold()));
            if (firstLineCount.values().stream().anyMatch(count -> count >= threshold)) {
                return true;
            }
        }
        return detectHeaderByPattern(documents);
    }

    boolean detectRepeatedFooter(List<Document> documents) {
        if (documents.size() < 2) {
            return false;
        }
        if (documents.size() >= 3) {
            Map<String, Integer> lastLineCount = countFirstOrLastLines(documents, false);
            int threshold = Math.max(2, (int) Math.ceil(documents.size() * properties.getHeaderFooterThreshold()));
            if (lastLineCount.values().stream().anyMatch(count -> count >= threshold)) {
                return true;
            }
        }
        return detectFooterByPattern(documents);
    }

    private boolean detectHeaderByPattern(List<Document> documents) {
        int patternMatchCount = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) {
                continue;
            }
            List<String> lines = doc.getText().lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
            if (lines.isEmpty()) {
                continue;
            }
            if (DATETIME_HEADER_PATTERN.matcher(lines.getFirst()).matches()) {
                patternMatchCount++;
            }
        }
        return patternMatchCount >= 2;
    }

    private boolean detectFooterByPattern(List<Document> documents) {
        int patternMatchCount = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) {
                continue;
            }
            List<String> lines = doc.getText().lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
            if (lines.isEmpty()) {
                continue;
            }
            if (PAGE_NUMBER_PATTERN.matcher(lines.getLast()).matches()) {
                patternMatchCount++;
            }
        }
        return patternMatchCount >= 1;
    }

    boolean detectTooManyBlankLines(List<Document> documents) {
        long totalLines = 0;
        long blankLines = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) {
                continue;
            }
            String text = doc.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String[] lines = text.split("\\r?\\n", -1);
            for (String line : lines) {
                totalLines++;
                if (line.isBlank()) {
                    blankLines++;
                }
            }
        }
        if (totalLines == 0) {
            return false;
        }
        return (double) blankLines / totalLines > properties.getBlankLineRatioThreshold();
    }

    boolean detectWeakParagraphStructure(List<Document> documents) {
        long totalParagraphs = 0;
        long totalParagraphLines = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) {
                continue;
            }
            String text = doc.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String[] paragraphs = text.split("\\n{2,}");
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                totalParagraphs++;
                totalParagraphLines += trimmed.split("\\r?\\n", -1).length;
            }
        }
        if (totalParagraphs == 0) {
            return false;
        }
        return (double) totalParagraphLines / totalParagraphs < properties.getWeakParagraphAvgLines();
    }

    boolean detectOcrNoise(List<Document> documents) {
        int ocrDocs = 0;
        int noisyDocs = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) {
                continue;
            }
            Object parseMode = doc.getMetadata().get("parseMode");
            if (parseMode != null && "OCR".equalsIgnoreCase(parseMode.toString())) {
                ocrDocs++;
            }
            String text = doc.getText();
            if (StringUtils.hasText(text) && OCR_NOISE_PATTERN.matcher(text).find()) {
                noisyDocs++;
            }
        }
        if (ocrDocs == 0 && noisyDocs == 0) {
            return false;
        }
        return noisyDocs > 0;
    }

    boolean detectSymbolDensityHigh(List<Document> documents) {
        long totalNonWhitespace = 0;
        long symbolCount = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) {
                continue;
            }
            String text = doc.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            for (char c : text.toCharArray()) {
                if (!Character.isWhitespace(c)) {
                    totalNonWhitespace++;
                }
            }
            var matcher = SPECIAL_SYMBOL_PATTERN.matcher(text);
            while (matcher.find()) {
                symbolCount++;
            }
        }
        if (totalNonWhitespace == 0) {
            return false;
        }
        return (double) symbolCount / totalNonWhitespace > properties.getSymbolDensityThreshold();
    }

    boolean detectTocOutlineMissing(List<Document> documents) {
        boolean hasToc = false;
        boolean hasTocEntries = false;
        boolean hasChapterHeadings = false;

        for (Document doc : documents) {
            if (doc == null || !doc.isText()) {
                continue;
            }
            String text = doc.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (TOC_PATTERN.matcher(trimmed).matches()) {
                    hasToc = true;
                }
                if (TOC_ENTRY_PATTERN.matcher(trimmed).find()) {
                    hasTocEntries = true;
                }
                if (CHAPTER_HEADING_PATTERN.matcher(trimmed).find()) {
                    hasChapterHeadings = true;
                }
            }
        }

        if (hasToc || hasTocEntries) {
            return false;
        }
        return !hasChapterHeadings;
    }

    private Map<String, Integer> countFirstOrLastLines(List<Document> documents, boolean first) {
        Map<String, Integer> count = new HashMap<>();
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) {
                continue;
            }
            List<String> lines = doc.getText().lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
            if (lines.isEmpty()) {
                continue;
            }
            String line = first ? lines.getFirst() : lines.getLast();
            count.put(line, count.getOrDefault(line, 0) + 1);
        }
        return count;
    }

    private static final Pattern PIPE_TABLE_ROW = Pattern.compile("^\\|.+\\|$");
    private static final Pattern PIPE_TABLE_SEP = Pattern.compile("^\\|[-:]+[-| :]*\\|$");
    private static final Pattern IMAGE_REF = Pattern.compile("!\\[[^\\]]*\\]\\([^)]+\\)");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.+");

    boolean detectMarkdownTable(List<Document> documents) {
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) continue;
            String text = doc.getText();
            if (text != null) {
                boolean hasRow = PIPE_TABLE_ROW.matcher(text).find();
                boolean hasSep = PIPE_TABLE_SEP.matcher(text).find();
                if (hasRow && hasSep) return true;
            }
        }
        return false;
    }

    boolean detectMarkdownImage(List<Document> documents) {
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) continue;
            String text = doc.getText();
            if (text != null && IMAGE_REF.matcher(text).find()) return true;
        }
        return false;
    }

    boolean detectMarkdownHeading(List<Document> documents) {
        int headingCount = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) continue;
            String text = doc.getText();
            if (text == null) continue;
            for (String line : text.split("\\r?\\n")) {
                if (MARKDOWN_HEADING.matcher(line.trim()).matches()) {
                    headingCount++;
                }
            }
        }
        return headingCount >= 2;
    }

    double computeTableRatio(List<Document> documents) {
        int tableLines = 0;
        int totalLines = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) continue;
            String text = doc.getText();
            if (text == null) continue;
            for (String line : text.lines().map(String::trim).filter(StringUtils::hasText).toList()) {
                totalLines++;
                if (PIPE_TABLE_ROW.matcher(line).matches()) tableLines++;
            }
        }
        return totalLines == 0 ? 0.0 : (double) tableLines / totalLines;
    }

    double computeImageRatio(List<Document> documents) {
        int imageChars = 0;
        int totalChars = 0;
        for (Document doc : documents) {
            if (doc == null || !doc.isText()) continue;
            String text = doc.getText();
            if (text == null) continue;
            totalChars += text.length();
            var matcher = IMAGE_REF.matcher(text);
            while (matcher.find()) {
                imageChars += matcher.group().length();
            }
        }
        return totalChars == 0 ? 0.0 : (double) imageChars / totalChars;
    }
}
