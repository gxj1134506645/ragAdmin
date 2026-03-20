package com.ragadmin.server.infra.ai.chat;

/**
 * 会话摘要生成结果。
 *
 * @param summaryText      摘要文本
 * @param source           摘要来源
 * @param inputMessageSize 输入消息条数
 */
public record ConversationSummaryResult(
        String summaryText,
        ConversationSummarySource source,
        int inputMessageSize
) {
}

