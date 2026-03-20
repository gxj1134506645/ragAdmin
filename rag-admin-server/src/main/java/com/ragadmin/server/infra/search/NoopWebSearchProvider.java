package com.ragadmin.server.infra.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 未接入真实联网搜索供应商时的兜底实现。
 * 联网开关开启后不会报错，只返回空结果并保留日志。
 */
@Component
@ConditionalOnMissingBean(WebSearchProvider.class)
public class NoopWebSearchProvider implements WebSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(NoopWebSearchProvider.class);

    @Override
    public List<WebSearchSnippet> search(String query, int topK) {
        if (StringUtils.hasText(query)) {
            log.debug("联网搜索已跳过，当前未配置真实搜索提供方，query={}, topK={}", query, topK);
        }
        return List.of();
    }
}
