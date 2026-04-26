package com.ragadmin.server.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieval.query-rewriting")
public class QueryRewritingProperties {

    private int multiQueryCount = 3;
    private int logMaxQueryLength = 120;

    public int getMultiQueryCount() {
        return multiQueryCount;
    }

    public void setMultiQueryCount(int multiQueryCount) {
        this.multiQueryCount = multiQueryCount;
    }

    public int getLogMaxQueryLength() {
        return logMaxQueryLength;
    }

    public void setLogMaxQueryLength(int logMaxQueryLength) {
        this.logMaxQueryLength = logMaxQueryLength;
    }
}
