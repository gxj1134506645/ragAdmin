package com.ragadmin.server.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieval")
public class RetrievalProperties {

    private int defaultTopK = 5;
    private int maxContextChunks = 5;
    private int maxContextChars = 4000;
    private int referenceSnippetChars = 200;

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public int getMaxContextChunks() {
        return maxContextChunks;
    }

    public void setMaxContextChunks(int maxContextChunks) {
        this.maxContextChunks = maxContextChunks;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public int getReferenceSnippetChars() {
        return referenceSnippetChars;
    }

    public void setReferenceSnippetChars(int referenceSnippetChars) {
        this.referenceSnippetChars = referenceSnippetChars;
    }
}
