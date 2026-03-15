package com.ragadmin.server.document.parser;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.document.ocr")
public class DocumentOcrProperties {

    private boolean enabled = false;
    private String tesseractCommand = "tesseract";
    private String language = "chi_sim+eng";
    private int maxPdfPages = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTesseractCommand() {
        return tesseractCommand;
    }

    public void setTesseractCommand(String tesseractCommand) {
        this.tesseractCommand = tesseractCommand;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getMaxPdfPages() {
        return maxPdfPages;
    }

    public void setMaxPdfPages(int maxPdfPages) {
        this.maxPdfPages = maxPdfPages;
    }
}
