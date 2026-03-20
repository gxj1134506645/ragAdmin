package com.ragadmin.server.infra.search;

import java.time.Instant;

/**
 * 联网搜索返回的标准化摘要片段。
 */
public record WebSearchSnippet(
        String title,
        String snippet,
        String url,
        Instant publishedAt
) {
}
