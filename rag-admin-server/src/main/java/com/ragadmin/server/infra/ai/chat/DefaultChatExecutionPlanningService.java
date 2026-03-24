package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.infra.ai.AiProviderExceptionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 基于结构化输出的问答前置规划器。
 * 当前先解决“是否检索、是否联网、查询重写”三类中间态决策，失败时必须回退到保守规则。
 */
@Component
public class DefaultChatExecutionPlanningService implements ChatExecutionPlanningService {

    private static final Logger log = LoggerFactory.getLogger(DefaultChatExecutionPlanningService.class);

    private final ConversationChatClient conversationChatClient;

    private final ChatExecutionPlanningProperties planningProperties;

    private final PromptTemplateService promptTemplateService;

    @Value("classpath:prompts/ai/chat/execution-planning-system.st")
    private Resource planningSystemPromptTemplate;

    @Value("classpath:prompts/ai/chat/execution-planning-user.st")
    private Resource planningUserPromptTemplate;

    public DefaultChatExecutionPlanningService(
            ConversationChatClient conversationChatClient,
            ChatExecutionPlanningProperties planningProperties,
            PromptTemplateService promptTemplateService
    ) {
        this.conversationChatClient = conversationChatClient;
        this.planningProperties = planningProperties;
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public ChatExecutionPlan plan(ChatExecutionPlanningRequest request) {
        ChatExecutionPlan fallbackPlan = buildRuleBasedPlan(request);
        if (!canUseModel(request)) {
            logPlan(request, fallbackPlan);
            return fallbackPlan;
        }

        try {
            ChatExecutionPlanStructuredOutput output = conversationChatClient.chatEntity(
                    request.providerCode(),
                    request.modelCode(),
                    buildPromptMessages(request),
                    ChatExecutionPlanStructuredOutput.class
            );
            ChatExecutionPlan plan = mergePlan(request, fallbackPlan, output);
            logPlan(request, plan);
            return plan;
        } catch (Exception ex) {
            if (AiProviderExceptionSupport.isProviderAccountIssue(ex)) {
                log.warn(
                        "问答规划模型调用失败，已回退规则规划，providerCode={}, modelCode={}, retrievalAvailable={}, webSearchAvailable={}, reason={}",
                        request.providerCode(),
                        request.modelCode(),
                        request.retrievalAvailable(),
                        request.webSearchAvailable(),
                        AiProviderExceptionSupport.summarize(ex)
                );
            } else {
                log.warn(
                        "问答规划模型调用失败，已回退规则规划，providerCode={}, modelCode={}, retrievalAvailable={}, webSearchAvailable={}",
                        request.providerCode(),
                        request.modelCode(),
                        request.retrievalAvailable(),
                        request.webSearchAvailable(),
                        ex
                );
            }
            logPlan(request, fallbackPlan);
            return fallbackPlan;
        }
    }

    private boolean canUseModel(ChatExecutionPlanningRequest request) {
        if (!planningProperties.isEnabled()) {
            return false;
        }
        if (request == null || !StringUtils.hasText(request.question())) {
            return false;
        }
        if (!request.retrievalAvailable() && !request.webSearchAvailable()) {
            return false;
        }
        return StringUtils.hasText(request.providerCode()) && StringUtils.hasText(request.modelCode());
    }

    private List<ChatPromptMessage> buildPromptMessages(ChatExecutionPlanningRequest request) {
        return List.of(
                new ChatPromptMessage("system", promptTemplateService.load(planningSystemPromptTemplate)),
                new ChatPromptMessage("user", promptTemplateService.render(planningUserPromptTemplate, Map.of(
                        "question", defaultText(request.question()),
                        "knowledge_base_scene", String.valueOf(request.knowledgeBaseScene()),
                        "selected_knowledge_base_count", String.valueOf(request.selectedKnowledgeBaseCount()),
                        "retrieval_available", String.valueOf(request.retrievalAvailable()),
                        "web_search_available", String.valueOf(request.webSearchAvailable())
                )))
        );
    }

    private ChatExecutionPlan mergePlan(
            ChatExecutionPlanningRequest request,
            ChatExecutionPlan fallbackPlan,
            ChatExecutionPlanStructuredOutput output
    ) {
        boolean needRetrieval = request.retrievalAvailable()
                && (output == null || output.needRetrieval() == null ? fallbackPlan.needRetrieval() : output.needRetrieval());
        boolean needWebSearch = request.webSearchAvailable()
                && (output == null || output.needWebSearch() == null ? fallbackPlan.needWebSearch() : output.needWebSearch());

        String retrievalQuery = needRetrieval
                ? firstNonBlank(output == null ? null : output.retrievalQuery(), fallbackPlan.retrievalQuery())
                : "";
        String webSearchQuery = needWebSearch
                ? firstNonBlank(output == null ? null : output.webSearchQuery(), fallbackPlan.webSearchQuery())
                : "";
        String intent = deriveIntent(
                firstNonBlank(output == null ? null : output.intent(), null),
                needRetrieval,
                needWebSearch
        );
        String reason = firstNonBlank(output == null ? null : output.reason(), fallbackPlan.reason());

        return new ChatExecutionPlan(
                intent,
                needRetrieval,
                retrievalQuery,
                needWebSearch,
                webSearchQuery,
                reason,
                "MODEL"
        );
    }

    private ChatExecutionPlan buildRuleBasedPlan(ChatExecutionPlanningRequest request) {
        if (request == null || !StringUtils.hasText(request.question())) {
            return new ChatExecutionPlan("GENERAL_QA", false, "", false, "", "问题为空，跳过规划", "RULE_BASED");
        }

        boolean needRetrieval = request.retrievalAvailable();
        boolean needWebSearch = request.webSearchAvailable();
        String reason;
        if (needRetrieval && needWebSearch) {
            reason = "当前同时具备知识库与联网能力，默认两者都参与增强。";
        } else if (needRetrieval) {
            reason = "当前问题具备知识库检索条件，默认执行检索。";
        } else if (needWebSearch) {
            reason = "当前问题开启联网能力，默认执行联网搜索。";
        } else {
            reason = "当前没有可用增强能力，按普通问答处理。";
        }
        return new ChatExecutionPlan(
                deriveIntent(null, needRetrieval, needWebSearch),
                needRetrieval,
                needRetrieval ? request.question() : "",
                needWebSearch,
                needWebSearch ? request.question() : "",
                reason,
                "RULE_BASED"
        );
    }

    private String deriveIntent(String rawIntent, boolean needRetrieval, boolean needWebSearch) {
        if (StringUtils.hasText(rawIntent)) {
            return rawIntent.trim();
        }
        if (needRetrieval && needWebSearch) {
            return "KNOWLEDGE_BASE_AND_WEB_SEARCH";
        }
        if (needRetrieval) {
            return "KNOWLEDGE_BASE_QA";
        }
        if (needWebSearch) {
            return "WEB_SEARCH_QA";
        }
        return "GENERAL_QA";
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return "";
    }

    private void logPlan(ChatExecutionPlanningRequest request, ChatExecutionPlan plan) {
        if (!planningProperties.isLogPlan() || request == null || plan == null) {
            return;
        }
        log.info(
                "问答规划完成，source={}, intent={}, needRetrieval={}, needWebSearch={}, retrievalQueryLength={}, webSearchQueryLength={}, retrievalAvailable={}, webSearchAvailable={}, selectedKnowledgeBaseCount={}, knowledgeBaseScene={}, providerCode={}, modelCode={}",
                plan.source(),
                plan.intent(),
                plan.needRetrieval(),
                plan.needWebSearch(),
                safeLength(plan.retrievalQuery()),
                safeLength(plan.webSearchQuery()),
                request.retrievalAvailable(),
                request.webSearchAvailable(),
                request.selectedKnowledgeBaseCount(),
                request.knowledgeBaseScene(),
                request.providerCode(),
                request.modelCode()
        );
    }

    private int safeLength(String text) {
        return StringUtils.hasText(text) ? text.trim().length() : 0;
    }

    private String defaultText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }
}
