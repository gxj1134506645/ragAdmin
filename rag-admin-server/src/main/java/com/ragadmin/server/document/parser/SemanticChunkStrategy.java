package com.ragadmin.server.document.parser;

import com.ragadmin.server.infra.ai.embedding.EmbeddingClientRegistry;
import com.ragadmin.server.infra.ai.embedding.EmbeddingModelClient;
import com.ragadmin.server.knowledge.entity.KnowledgeBaseEntity;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
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
@Order(6)
public class SemanticChunkStrategy implements DocumentChunkStrategy {

    private static final Logger log = LoggerFactory.getLogger(SemanticChunkStrategy.class);

    @Autowired
    private ChunkProperties chunkProperties;

    @Autowired
    private ModelService modelService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private EmbeddingClientRegistry embeddingClientRegistry;

    @Override
    public boolean supports(ChunkContext context) {
        return context.properties() != null
                && context.document() != null
                && context.document().getKbId() != null
                && !context.contentContainsTable()
                && !context.contentContainsImage();
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

        List<String> childTexts = splitIntoChildUnits(fullText, chunkProperties.getSemantic().getChildMaxChars());
        if (childTexts.size() <= 1) {
            return childTexts.stream()
                    .map(t -> new ChunkDraft(t, new HashMap<>()))
                    .toList();
        }

        EmbeddingModelDescriptor embeddingDesc;
        try {
            embeddingDesc = resolveEmbeddingDescriptor(context);
            if (embeddingDesc == null) {
                throw new IllegalStateException("无法解析 embedding 模型，kbId=" + context.document().getKbId());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Embedding 模型不可用: " + e.getMessage(), e);
        }

        List<List<Float>> embeddings = computeEmbeddings(childTexts, embeddingDesc);
        if (embeddings == null || embeddings.size() != childTexts.size()) {
            return childTexts.stream()
                    .map(t -> new ChunkDraft(t, new HashMap<>()))
                    .toList();
        }

        List<Double> similarities = computeAdjacentSimilarities(embeddings);
        List<Integer> breakpoints = findBreakpoints(similarities, chunkProperties.getSemantic().getSimilarityThreshold());
        log.info("语义分块完成，childUnits={} breakpoints={}", childTexts.size(), breakpoints.size());

        return buildParentChildDrafts(childTexts, breakpoints, chunkProperties.getSemantic().getParentMaxChars());
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
            long parentId = parentIdCounter--;
            Map<String, Object> parentMeta = new HashMap<>();
            parentMeta.put("isParent", true);
            parentMeta.put("chunkStrategy", "SEMANTIC");
            parentMeta.put("childCount", group.size());
            parentMeta.put("parentPlaceholderId", parentId);
            drafts.add(new ChunkDraft(parentText, parentMeta, null));

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
            Long kbId = context.document().getKbId();
            if (kbId == null) {
                return null;
            }
            KnowledgeBaseEntity kb = knowledgeBaseService.requireById(kbId);
            if (kb.getEmbeddingModelId() == null) {
                return null;
            }
            return modelService.resolveKnowledgeBaseEmbeddingModelDescriptor(kb.getEmbeddingModelId());
        } catch (Exception e) {
            log.warn("解析 embedding 模型描述符失败: {}", e.getMessage());
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
