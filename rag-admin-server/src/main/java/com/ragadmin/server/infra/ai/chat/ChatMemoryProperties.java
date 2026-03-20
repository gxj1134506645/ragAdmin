package com.ragadmin.server.infra.ai.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.chat.memory")
public class ChatMemoryProperties {

    /**
     * 兼容 Spring AI memory 的兜底消息窗口。
     */
    private int maxMessages = 20;

    /**
     * 短期热记忆保留轮数。每轮默认是一问一答两条消息。
     */
    private int shortTermRounds = 10;

    /**
     * 短期热记忆空闲过期时间，单位分钟。
     */
    private int shortTermIdleTtlMinutes = 120;

    /**
     * 摘要触发轮数阈值。
     */
    private int summaryTriggerRounds = 10;

    /**
     * 摘要触发的估算 token 阈值。
     */
    private int summaryTriggerTokenThreshold = 4000;

    /**
     * 长期摘要的最大长度。
     */
    private int summaryMaxLength = 2000;

    /**
     * 摘要刷新锁超时时间，单位秒。
     */
    private int summaryLockSeconds = 30;

    /**
     * 会话记忆后台刷新最大并发数。
     */
    private int refreshMaxConcurrency = 8;

    /**
     * Redis key 前缀。
     */
    private String redisKeyPrefix = "rag:chat:memory";

    public int getShortTermMessageLimit() {
        return Math.max(1, shortTermRounds) * 2;
    }
}