package com.ragadmin.server.infra.ai.chat;

/**
 * 会话摘要生成器。
 * 支持优先走模型摘要，并在不可用时回退规则摘要。
 */
public interface ConversationSummaryGenerator {

    ConversationSummaryResult generate(ConversationSummaryRequest request);
}

