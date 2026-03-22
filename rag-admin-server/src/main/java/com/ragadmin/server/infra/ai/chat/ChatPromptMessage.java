package com.ragadmin.server.infra.ai.chat;

/**
 * 面向模型调用的标准聊天消息。
 * 该对象只描述角色与文本内容，不绑定任何厂商 SDK 类型，便于在编排层和适配层之间稳定传递。
 */
public record ChatPromptMessage(
        String role,
        String content
) {
}
