package com.ragadmin.server.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "rag.retrieval.query-preprocess")
public class QueryPreprocessProperties {

    private boolean enabled = true;

    @NestedConfigurationProperty
    private PiiMaskProperties piiMask = new PiiMaskProperties();

    @NestedConfigurationProperty
    private ContentFilterProperties contentFilter = new ContentFilterProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public PiiMaskProperties getPiiMask() {
        return piiMask;
    }

    public void setPiiMask(PiiMaskProperties piiMask) {
        this.piiMask = piiMask;
    }

    public ContentFilterProperties getContentFilter() {
        return contentFilter;
    }

    public void setContentFilter(ContentFilterProperties contentFilter) {
        this.contentFilter = contentFilter;
    }

    public static class PiiMaskProperties {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ContentFilterProperties {
        private boolean enabled = true;
        private boolean blockEnabled = false;
        private List<String> blockedWords = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isBlockEnabled() {
            return blockEnabled;
        }

        public void setBlockEnabled(boolean blockEnabled) {
            this.blockEnabled = blockEnabled;
        }

        public List<String> getBlockedWords() {
            return blockedWords;
        }

        public void setBlockedWords(List<String> blockedWords) {
            this.blockedWords = blockedWords;
        }
    }
}
