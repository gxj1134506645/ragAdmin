package com.ragadmin.server.infra.ai.chat;

import java.util.List;

/**
 * 会话摘要请求参数。
 *
 * @param conversationId 会话标识（用于日志追踪）
 * @param providerCode   模型提供方编码
 * @param modelCode      模型编码
 * @param messages       待摘要的会话消息
 * @param maxLength      本次摘要最大长度（为空时回落到全局配置）
 */
public record ConversationSummaryRequest(
        String conversationId,
        String providerCode,
        String modelCode,
        List<ChatPromptMessage> messages,
        Integer maxLength
) {
}

