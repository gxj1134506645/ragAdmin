package com.ragadmin.server.retrieval.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragadmin.server.document.entity.ChunkEntity;
import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.PromptTemplateService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.config.RerankingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class RerankingService {

    private static final Logger log = LoggerFactory.getLogger(RerankingService.class);

    private static final int SNIPPET_MAX_CHARS = 300;

    @Autowired
    private ConversationChatClient conversationChatClient;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private RerankingProperties rerankingProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("classpath:prompts/ai/retrieval/reranking-system.st")
    private Resource systemPromptResource;

    @Value("classpath:prompts/ai/retrieval/reranking-user.st")
    private Resource userPromptResource;

    public record ScoredPassage(int index, double score) {
    }

    public List<RetrievalService.RetrievedChunk> rerank(String query, List<RetrievalService.RetrievedChunk> candidates) {
        if (candidates.size() <= 1 || !rerankingProperties.isEnabled()) {
            return candidates;
        }

        var chatModel = modelService.findDefaultChatModelDescriptor();
        if (chatModel == null) {
            log.debug("无可用聊天模型，跳过 reranking");
            return candidates;
        }

        int maxCandidates = Math.min(candidates.size(), rerankingProperties.getMaxCandidates());
        List<RetrievalService.RetrievedChunk> limited = candidates.subList(0, maxCandidates);

        try {
            List<ScoredPassage> scores = scorePassages(query, limited, chatModel.providerCode(), chatModel.modelCode());

            List<RetrievalService.RetrievedChunk> reranked = new ArrayList<>();
            for (ScoredPassage sp : scores) {
                if (sp.index() >= 0 && sp.index() < limited.size()) {
                    RetrievalService.RetrievedChunk original = limited.get(sp.index());
                    reranked.add(new RetrievalService.RetrievedChunk(original.chunk(), sp.score()));
                }
            }

            // Add remaining candidates not scored by LLM
            for (int i = 0; i < limited.size(); i++) {
                final int idx = i;
                if (reranked.stream().noneMatch(r -> r.chunk().getId().equals(limited.get(idx).chunk().getId()))) {
                    reranked.add(limited.get(i));
                }
            }

            reranked.sort(Comparator.comparing(RetrievalService.RetrievedChunk::score).reversed());

            int topN = Math.min(rerankingProperties.getTopN(), reranked.size());
            log.debug("Reranking 完成: candidates={}, scored={}, topN={}", candidates.size(), scores.size(), topN);
            return reranked.subList(0, topN);
        } catch (Exception e) {
            log.warn("Reranking 失败，返回原始排序: {}", e.getMessage());
            return candidates;
        }
    }

    private List<ScoredPassage> scorePassages(String query, List<RetrievalService.RetrievedChunk> candidates,
                                               String providerCode, String modelCode) throws Exception {
        String systemPrompt = promptTemplateService.load(systemPromptResource);
        String userTemplate = promptTemplateService.load(userPromptResource);

        StringBuilder passagesBuilder = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            String text = candidates.get(i).chunk().getChunkText();
            String snippet = text != null && text.length() > SNIPPET_MAX_CHARS
                    ? text.substring(0, SNIPPET_MAX_CHARS) + "..."
                    : (text != null ? text : "");
            passagesBuilder.append(i + 1).append(". ").append(snippet).append("\n\n");
        }

        String userPrompt = userTemplate
                .replace("{query}", query)
                .replace("{passages}", passagesBuilder.toString());

        List<ChatPromptMessage> messages = List.of(
                new ChatPromptMessage("system", systemPrompt),
                new ChatPromptMessage("user", userPrompt)
        );

        ChatCompletionResult result = conversationChatClient.chat(providerCode, modelCode, messages);
        return parseScoredPassages(result.content());
    }

    List<ScoredPassage> parseScoredPassages(String response) {
        List<ScoredPassage> scores = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return scores;
        }

        try {
            String json = response.trim();
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> item : items) {
                int index = ((Number) item.get("index")).intValue() - 1; // convert to 0-based
                double score = ((Number) item.get("score")).doubleValue();
                scores.add(new ScoredPassage(index, score));
            }

            scores.sort(Comparator.comparing(ScoredPassage::score).reversed());
        } catch (Exception e) {
            log.warn("解析 reranking 结果失败: {}", e.getMessage());
        }

        return scores;
    }
}
