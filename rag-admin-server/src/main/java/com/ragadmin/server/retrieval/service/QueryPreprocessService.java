package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.retrieval.config.QueryPreprocessProperties;
import com.ragadmin.server.retrieval.model.PiiMaskRule;
import com.ragadmin.server.retrieval.model.PreprocessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryPreprocessService {

    private static final Logger log = LoggerFactory.getLogger(QueryPreprocessService.class);

    @Autowired
    private QueryPreprocessProperties properties;

    @Autowired
    private ContentFilterService contentFilterService;

    public PreprocessResult preprocess(String query) {
        if (!properties.isEnabled() || query == null || query.isBlank()) {
            return PreprocessResult.unchanged(query);
        }

        try {
            String processed = query;
            boolean modified = false;

            // Step 1: PII 脱敏
            if (properties.getPiiMask().isEnabled()) {
                PiiMaskResult maskResult = maskPii(processed);
                if (maskResult.modified) {
                    processed = maskResult.text;
                    modified = true;
                }
            }

            // Step 2: 内容过滤
            ContentFilterService.ContentFilterResult filterResult = contentFilterService.filter(processed);
            if (filterResult.modified()) {
                processed = filterResult.text();
                modified = true;
            }

            if (modified) {
                log.info("Query 预处理完成: originalLength={}, processedLength={}", query.length(), processed.length());
            }

            return new PreprocessResult(processed, modified, filterResult.blocked());
        } catch (Exception e) {
            log.warn("Query 预处理失败，使用原始 query: {}", e.getMessage());
            return PreprocessResult.unchanged(query);
        }
    }

    private PiiMaskResult maskPii(String text) {
        List<PiiMaskRule> rules = PiiMaskRule.DEFAULT_RULES;
        String result = text;
        boolean modified = false;

        for (PiiMaskRule rule : rules) {
            String replaced = rule.pattern().matcher(result).replaceAll(rule.replacement());
            if (!replaced.equals(result)) {
                log.debug("PII 脱敏: 检测到 {}, 已替换", rule.name());
                result = replaced;
                modified = true;
            }
        }

        return new PiiMaskResult(result, modified);
    }

    private record PiiMaskResult(String text, boolean modified) {}
}
