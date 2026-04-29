package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.retrieval.config.QueryPreprocessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ContentFilterService {

    private static final Logger log = LoggerFactory.getLogger(ContentFilterService.class);

    private static final String MASK = "***";

    @Autowired
    private QueryPreprocessProperties properties;

    public ContentFilterResult filter(String text) {
        if (!properties.getContentFilter().isEnabled() || text == null || text.isBlank()) {
            return new ContentFilterResult(text, false, false);
        }

        List<String> blockedWords = properties.getContentFilter().getBlockedWords();
        if (blockedWords == null || blockedWords.isEmpty()) {
            return new ContentFilterResult(text, false, false);
        }

        // Case-insensitive matching, deduplicate and sort by length desc for longest match first
        Set<String> sortedWords = new TreeSet<>((a, b) -> {
            int cmp = Integer.compare(b.length(), a.length());
            return cmp != 0 ? cmp : a.compareTo(b);
        });
        sortedWords.addAll(blockedWords);

        String result = text;
        boolean modified = false;

        for (String word : sortedWords) {
            if (word == null || word.isBlank()) continue;
            if (result.toLowerCase().contains(word.toLowerCase())) {
                result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), MASK);
                modified = true;
            }
        }

        boolean blocked = false;
        if (modified && properties.getContentFilter().isBlockEnabled()) {
            blocked = true;
            log.info("Query 被内容过滤拦截: 包含违规内容");
        }

        if (modified) {
            log.debug("内容过滤: 替换了违规内容");
        }

        return new ContentFilterResult(result, modified, blocked);
    }

    public record ContentFilterResult(String text, boolean modified, boolean blocked) {}
}
