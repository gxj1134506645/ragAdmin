package com.ragadmin.server.infra.ai.chat;

/**
 * 模型聊天完成结果。
 * 当前统一保留回答文本与基础 token 统计，避免上层直接依赖底层模型响应结构。
 */
public record ChatCompletionResult(
        String content,
        Integer promptTokens,
        Integer completionTokens
) {
}
