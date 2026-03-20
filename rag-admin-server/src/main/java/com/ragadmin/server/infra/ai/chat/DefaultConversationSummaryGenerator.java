package com.ragadmin.server.infra.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 默认摘要生成实现：
 * 1. 优先使用已配置模型生成摘要
 * 2. 模型不可用或调用失败时，回退规则摘要
 */
@Component
public class DefaultConversationSummaryGenerator implements ConversationSummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultConversationSummaryGenerator.class);

    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是会话摘要助手。
            请基于给定的对话内容生成简洁摘要，保留关键事实、约束、待办和结论。
            输出纯文本，不要使用 markdown，不要编造不存在的信息。
            """;

    private static final String EMPTY_SUMMARY = "暂无可摘要的会话内容。";

    private final ChatClientRegistry chatClientRegistry;

    private final ChatMemoryProperties chatMemoryProperties;

    public DefaultConversationSummaryGenerator(
            ChatClientRegistry chatClientRegistry,
            ChatMemoryProperties chatMemoryProperties
    ) {
        this.chatClientRegistry = chatClientRegistry;
        this.chatMemoryProperties = chatMemoryProperties;
    }

    @Override
    public ConversationSummaryResult generate(ConversationSummaryRequest request) {
        List<ChatModelClient.ChatMessage> sourceMessages = request == null || request.messages() == null
                ? List.of()
                : request.messages();
        int maxLength = resolveMaxLength(request == null ? null : request.maxLength());

        if (canUseModel(request, sourceMessages)) {
            try {
                String summary = generateByModel(request, sourceMessages, maxLength);
                if (StringUtils.hasText(summary)) {
                    return new ConversationSummaryResult(summary, ConversationSummarySource.MODEL, sourceMessages.size());
                }
            } catch (Exception ex) {
                log.warn(
                        "会话摘要模型调用失败，已回退规则摘要，conversationId={}, providerCode={}, modelCode={}",
                        request.conversationId(),
                        request.providerCode(),
                        request.modelCode(),
                        ex
                );
            }
        }

        String fallbackSummary = generateByRule(sourceMessages, maxLength);
        return new ConversationSummaryResult(fallbackSummary, ConversationSummarySource.RULE_BASED, sourceMessages.size());
    }

    private boolean canUseModel(ConversationSummaryRequest request, List<ChatModelClient.ChatMessage> messages) {
        if (request == null || CollectionUtils.isEmpty(messages)) {
            return false;
        }
        return StringUtils.hasText(request.providerCode()) && StringUtils.hasText(request.modelCode());
    }

    private String generateByModel(
            ConversationSummaryRequest request,
            List<ChatModelClient.ChatMessage> messages,
            int maxLength
    ) {
        ChatModelClient chatModelClient = chatClientRegistry.getClient(request.providerCode());
        List<ChatModelClient.ChatMessage> promptMessages = new ArrayList<>();
        promptMessages.add(new ChatModelClient.ChatMessage("system", SUMMARY_SYSTEM_PROMPT));
        promptMessages.add(new ChatModelClient.ChatMessage("user", buildConversationText(messages)));
        ChatModelClient.ChatCompletionResult completion = chatModelClient.chat(request.modelCode(), promptMessages);
        return truncate(normalize(completion.content()), maxLength);
    }

    private String generateByRule(List<ChatModelClient.ChatMessage> messages, int maxLength) {
        if (CollectionUtils.isEmpty(messages)) {
            return truncate(EMPTY_SUMMARY, maxLength);
        }
        String summary = messages.stream()
                .filter(message -> message != null && StringUtils.hasText(message.content()))
                .map(message -> formatRole(message.role()) + "：" + normalize(message.content()))
                .collect(Collectors.joining("\n"));
        if (!StringUtils.hasText(summary)) {
            return truncate(EMPTY_SUMMARY, maxLength);
        }
        return truncate(summary, maxLength);
    }

    private String buildConversationText(List<ChatModelClient.ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message != null && StringUtils.hasText(message.content()))
                .map(message -> formatRole(message.role()) + "：" + normalize(message.content()))
                .collect(Collectors.joining("\n"));
    }

    private String formatRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "未知角色";
        }
        String roleUpper = role.trim().toUpperCase(Locale.ROOT);
        return switch (roleUpper) {
            case "USER" -> "用户";
            case "ASSISTANT" -> "助手";
            case "SYSTEM" -> "系统";
            default -> role.trim();
        };
    }

    private int resolveMaxLength(Integer maxLength) {
        int configuredMaxLength = Math.max(1, chatMemoryProperties.getSummaryMaxLength());
        if (maxLength == null) {
            return configuredMaxLength;
        }
        return Math.max(1, maxLength);
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}

