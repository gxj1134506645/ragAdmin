package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.PromptTemplateService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.retrieval.config.QueryRewritingMode;
import com.ragadmin.server.retrieval.config.QueryRewritingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryRewritingService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewritingService.class);

    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*\\d+[.、)）]\\s*(.+)$", Pattern.MULTILINE);

    @Autowired
    private ConversationChatClient conversationChatClient;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private QueryRewritingProperties properties;

    @Value("classpath:prompts/ai/retrieval/multi-query-decomposition.st")
    private Resource multiQueryPromptResource;

    @Value("classpath:prompts/ai/retrieval/hyde-generation.st")
    private Resource hydePromptResource;

    public record RewrittenQueries(List<String> queries) {
    }

    public RewrittenQueries rewrite(String originalQuery, String mode) {
        QueryRewritingMode rewritingMode = QueryRewritingMode.resolve(mode);
        if (rewritingMode == QueryRewritingMode.NONE) {
            return new RewrittenQueries(List.of(originalQuery));
        }

        var chatModel = modelService.findDefaultChatModelDescriptor();
        if (chatModel == null) {
            log.debug("无可用聊天模型，跳过查询改写");
            return new RewrittenQueries(List.of(originalQuery));
        }

        List<String> queries = new ArrayList<>();
        queries.add(originalQuery);

        try {
            if ((rewritingMode == QueryRewritingMode.MULTI_QUERY || rewritingMode == QueryRewritingMode.MULTI_QUERY_AND_HYDE)
                    && properties.isMultiQueryEnabled()) {
                queries.addAll(generateMultiQueries(originalQuery, chatModel.providerCode(), chatModel.modelCode()));
            }

            if ((rewritingMode == QueryRewritingMode.HYDE || rewritingMode == QueryRewritingMode.MULTI_QUERY_AND_HYDE)
                    && properties.isHydeEnabled()) {
                String hydeQuery = generateHydeQuery(originalQuery, chatModel.providerCode(), chatModel.modelCode());
                if (hydeQuery != null && !hydeQuery.isBlank()) {
                    queries.add(hydeQuery);
                }
            }
        } catch (Exception e) {
            log.warn("查询改写失败，回退到原始查询: {}", e.getMessage());
        }

        return new RewrittenQueries(queries);
    }

    private List<String> generateMultiQueries(String originalQuery, String providerCode, String modelCode) {
        try {
            String systemPrompt = promptTemplateService.load(multiQueryPromptResource);
            List<ChatPromptMessage> messages = List.of(
                    new ChatPromptMessage("system", systemPrompt),
                    new ChatPromptMessage("user", "原始问题：" + originalQuery + "\n\n请生成" + properties.getMultiQueryCount() + "个不同角度的替代问题。")
            );

            ChatCompletionResult result = conversationChatClient.chat(providerCode, modelCode, messages);
            String responseText = result.content();

            List<String> parsed = new ArrayList<>();
            Matcher matcher = NUMBERED_LINE.matcher(responseText != null ? responseText : "");
            while (matcher.find() && parsed.size() < properties.getMultiQueryCount()) {
                String line = matcher.group(1).trim();
                if (!line.isBlank()) {
                    parsed.add(line);
                }
            }

            log.debug("Multi-Query 分解: original={}, generated={}", originalQuery, parsed.size());
            return parsed;
        } catch (Exception e) {
            log.warn("Multi-Query 生成失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String generateHydeQuery(String originalQuery, String providerCode, String modelCode) {
        try {
            String systemPrompt = promptTemplateService.load(hydePromptResource);
            List<ChatPromptMessage> messages = List.of(
                    new ChatPromptMessage("system", systemPrompt),
                    new ChatPromptMessage("user", originalQuery)
            );

            ChatCompletionResult result = conversationChatClient.chat(providerCode, modelCode, messages);
            log.debug("HyDE 生成: original={}, length={}", originalQuery, result.content() != null ? result.content().length() : 0);
            return result.content();
        } catch (Exception e) {
            log.warn("HyDE 生成失败: {}", e.getMessage());
            return null;
        }
    }
}
