package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Order(5)
public class MarkdownChunkStrategy implements DocumentChunkStrategy {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+.+", Pattern.MULTILINE);
    private static final Pattern CODE_FENCE_START = Pattern.compile("^```", Pattern.MULTILINE);

    @Override
    public boolean supports(ChunkContext context) {
        return context.hasHeadingStructure()
                && !context.contentContainsTable()
                && !context.contentContainsImage();
    }

    @Override
    public List<ChunkDraft> chunk(List<Document> documents, ChunkContext context) {
        ChunkStrategyProperties props = context.properties();
        List<ChunkDraft> result = new ArrayList<>();
        for (Document document : documents) {
            String text = normalizeText(document.getText());
            if (text.isEmpty()) {
                continue;
            }
            List<String> sections = splitByHeadings(text);
            List<String> chunkTexts = aggregateSections(sections, props);
            for (int i = 0; i < chunkTexts.size(); i++) {
                Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
                metadata.put("parentDocumentId", document.getId());
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunkTexts.size());
                result.add(new ChunkDraft(chunkTexts.get(i), metadata));
            }
        }
        return result;
    }

    List<String> splitByHeadings(String text) {
        List<String> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        boolean inCodeBlock = false;

        for (String line : lines) {
            if (CODE_FENCE_START.matcher(line).find()) {
                inCodeBlock = !inCodeBlock;
                current.append(line).append("\n");
                continue;
            }

            if (!inCodeBlock && HEADING_PATTERN.matcher(line).find() && current.length() > 0) {
                String section = current.toString().trim();
                if (!section.isEmpty()) {
                    sections.add(section);
                }
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            sections.add(last);
        }
        return sections;
    }

    List<String> aggregateSections(List<String> sections, ChunkStrategyProperties props) {
        int maxChars = props.maxChunkCharsSafe();
        int overlapChars = props.overlapCharsSafe();
        RecursiveFallbackStrategy fallback = new RecursiveFallbackStrategy();

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chunkNo = 0;

        for (String section : sections) {
            if (current.length() > 0 && current.length() + section.length() + 2 > maxChars) {
                chunks.add(current.toString().trim());
                chunkNo++;
                String overlap = fallback.overlapTail(chunks.get(chunkNo - 1), overlapChars);
                current = new StringBuilder(overlap);
            }
            if (current.length() > 0 && !(current.length() == overlapChars || current.toString().equals(chunks.isEmpty() ? "" : fallback.overlapTail(chunks.get(chunkNo - 1), overlapChars)))) {
                current.append("\n\n");
            }
            current.append(section);
        }

        if (current.length() > 0) {
            String trimmed = current.toString().trim();
            if (!trimmed.isEmpty()) {
                chunks.add(trimmed);
            }
        }

        if (chunks.isEmpty() && !sections.isEmpty()) {
            for (String section : sections) {
                if (section.length() > maxChars) {
                    chunks.addAll(fallback.splitText(section, props));
                } else {
                    chunks.add(section);
                }
            }
        }
        return chunks;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").trim();
    }
}
