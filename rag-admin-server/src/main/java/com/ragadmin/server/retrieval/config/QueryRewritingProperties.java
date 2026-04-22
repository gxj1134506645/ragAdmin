package com.ragadmin.server.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieval.query-rewriting")
public class QueryRewritingProperties {

    private boolean multiQueryEnabled = false;
    private int multiQueryCount = 3;
    private boolean hydeEnabled = false;

    public boolean isMultiQueryEnabled() {
        return multiQueryEnabled;
    }

    public void setMultiQueryEnabled(boolean multiQueryEnabled) {
        this.multiQueryEnabled = multiQueryEnabled;
    }

    public int getMultiQueryCount() {
        return multiQueryCount;
    }

    public void setMultiQueryCount(int multiQueryCount) {
        this.multiQueryCount = multiQueryCount;
    }

    public boolean isHydeEnabled() {
        return hydeEnabled;
    }

    public void setHydeEnabled(boolean hydeEnabled) {
        this.hydeEnabled = hydeEnabled;
    }
}
