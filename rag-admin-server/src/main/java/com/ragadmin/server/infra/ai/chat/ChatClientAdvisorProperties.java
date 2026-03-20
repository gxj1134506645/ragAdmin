package com.ragadmin.server.infra.ai.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.ai.chat.client")
public class ChatClientAdvisorProperties {

    /**
     * 是否启用 Spring AI SimpleLoggerAdvisor。
     * 该日志 advisor 会输出模型请求与响应正文，默认只建议在本地或开发环境开启。
     */
    private boolean simpleLoggerAdvisorEnabled = false;

    /**
     * SimpleLoggerAdvisor 单条文本的最大日志字符数。
     */
    private int simpleLoggerMaxTextLength = 800;
}
