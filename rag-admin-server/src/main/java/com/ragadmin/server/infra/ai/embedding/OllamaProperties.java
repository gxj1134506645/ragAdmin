package com.ragadmin.server.infra.ai.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.ai.ollama")
public class OllamaProperties {

    private boolean enabled = false;
    private String baseUrl;
    private String defaultEmbeddingModel;
    private int timeoutSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    public void setDefaultEmbeddingModel(String defaultEmbeddingModel) {
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
