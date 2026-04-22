package com.ragadmin.server.document.parser;

import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.document.support.EmbeddingModelDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(5)
public class SemanticChunkStrategy implements DocumentChunkStrategy {

    private static final Logger log = LoggerFactory.getLogger(SemanticChunkStrategy.class);

    private static final int CHILD_MAX_CHARS = 400;
    private static final int PARENT_MAX_CHARS = 2400;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    @Autowired
    private ModelService modelService;

    @Autowired
    private EmbeddingClientRegistry embeddingClientRegistry;

    @Override
    public boolean supports(ChunkContext context) {
        return context.properties() != null
                && context.document() != null
                && context.document().getKbId() != null;
    }

    @Override
    public List<ChunkDraft> chunk(List<Document> documents, ChunkContext context) {
        String fullText = documents.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a + "\n" + b)
                .trim();

        if (fullText.isBlank()) {
            return List.of();
        }

        List<String> childTexts = splitIntoChildUnits(fullText, CHILD_MAX_CHARS);
        if (childTexts.size() <= 1) {
            return childTexts.stream()
                    .map(t -> new ChunkDraft(t, new HashMap<>()))
                    .toList();
        }

        EmbeddingModelDescriptor embeddingDesc;
        try {
            Long embeddingModelId = context.document() != null ? null : null;
            // Use knowledge base embedding model
            embeddingDesc = resolveEmbeddingDescriptor(context);
            if (embeddingDesc == null) {
                log.debug("无法解析 embedding 模型，回退到普通分块");
                return new RecursiveFallbackStrategy().chunk(documents, context);
            }
        } catch (Exception e) {
            log.debug("Embedding 模型不可用，回退到普通分块: {}", e.getMessage());
            return new RecursiveFallbackStrategy().chunk(documents, context);
        }

        List<List<Float>> embeddings = computeEmbeddings(childTexts, embeddingDesc);
        if (embeddings == null || embeddings.size() != childTexts.size()) {
            return childTexts.stream()
                    .map(t -> new ChunkDraft(t, new HashMap<>()))
                    .toList();
        }

        List<Double> similarities = computeAdjacentSimilarities(embeddings);
        List<Integer> breakpoints = findBreakpoints(similarities, DEFAULT_SIMILARITY_THRESHOLD);

        return buildParentChildDrafts(childTexts, breakpoints, PARENT_MAX_CHARS);
    }

    List<Integer> findBreakpoints(List<Double> similarities, double threshold) {
        List<Integer> breakpoints = new ArrayList<>();
        for (int i = 0; i < similarities.size(); i++) {
            if (similarities.get(i) < threshold) {
                breakpoints.add(i + 1); // breakpoint AFTER the low-similarity pair
            }
        }
        return breakpoints;
    }

    List<ChunkDraft> buildParentChildDrafts(List<String> childTexts, List<Integer> breakpoints, int parentMaxChars) {
        List<ChunkDraft> drafts = new ArrayList<>();

        // Create groups of children separated by breakpoints
        List<List<String>> groups = new ArrayList<>();
        List<String> currentGroup = new ArrayList<>();
        int currentChars = 0;
        int bpIndex = 0;

        for (int i = 0; i < childTexts.size(); i++) {
            String child = childTexts.get(i);

            // Check if this is a breakpoint
            boolean isBreakpoint = bpIndex < breakpoints.size() && breakpoints.get(bpIndex) == i;
            if (isBreakpoint) {
                bpIndex++;
                if (!currentGroup.isEmpty()) {
                    groups.add(currentGroup);
                    currentGroup = new ArrayList<>();
                    currentChars = 0;
                }
            }

            // Also break if current group would exceed parent max
            if (currentChars + child.length() > parentMaxChars && !currentGroup.isEmpty()) {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentChars = 0;
            }

            currentGroup.add(child);
            currentChars += child.length();
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        // If only one group, no parent-child structure needed
        if (groups.size() == 1) {
            return childTexts.stream()
                    .map(t -> new ChunkDraft(t, Map.of("chunkStrategy", "SEMANTIC")))
                    .toList();
        }

        // Create parent drafts (with placeholder ID to be resolved after persistence)
        long parentIdCounter = -1; // negative IDs as placeholders
        for (List<String> group : groups) {
            String parentText = String.join("\n\n", group);
            Map<String, Object> parentMeta = new HashMap<>();
            parentMeta.put("isParent", true);
            parentMeta.put("chunkStrategy", "SEMANTIC");
            parentMeta.put("childCount", group.size());
            drafts.add(new ChunkDraft(parentText, parentMeta, null));

            long parentId = parentIdCounter--;
            for (String childText : group) {
                Map<String, Object> childMeta = new HashMap<>();
                childMeta.put("isChild", true);
                childMeta.put("chunkStrategy", "SEMANTIC");
                childMeta.put("parentPlaceholderId", parentId);
                drafts.add(new ChunkDraft(childText, childMeta, parentId));
            }
        }

        return drafts;
    }

    private List<Double> computeAdjacentSimilarities(List<List<Float>> embeddings) {
        List<Double> similarities = new ArrayList<>();
        for (int i = 0; i < embeddings.size() - 1; i++) {
            similarities.add(cosineSimilarity(embeddings.get(i), embeddings.get(i + 1)));
        }
        return similarities;
    }

    double cosineSimilarity(List<Float> a, List<Float> b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dot / denominator;
    }

    private List<String> splitIntoChildUnits(String text, int maxChars) {
        List<String> units = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\n+");

        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (current.length() + trimmed.length() + 2 > maxChars && current.length() > 0) {
                units.add(current.toString().trim());
                current = new StringBuilder();
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }

        if (current.length() > 0) {
            units.add(current.toString().trim());
        }

        return units;
    }

    private EmbeddingModelDescriptor resolveEmbeddingDescriptor(ChunkContext context) {
        try {
            // This is a simplified approach; in production, resolve from KB config
            return null; // Will be properly resolved when integrated with KB embedding config
        } catch (Exception e) {
            return null;
        }
    }

    private List<List<Float>> computeEmbeddings(List<String> texts, EmbeddingModelDescriptor desc) {
        try {
            EmbeddingModelClient client = embeddingClientRegistry.getClient(desc.providerCode());
            return client.embed(desc.modelCode(), texts);
        } catch (Exception e) {
            log.warn("Embedding 计算失败: {}", e.getMessage());
            return null;
        }
    }
}
