package com.ragadmin.server.infra.ai.chat;

/**
 * 会话摘要的结构化输出载荷。
 * 当前先收敛为单字段，后续如需补充事实、待办、风险等维度，再在此对象上扩展。
 */
public record ConversationSummaryStructuredOutput(
        String summary
) {
}
