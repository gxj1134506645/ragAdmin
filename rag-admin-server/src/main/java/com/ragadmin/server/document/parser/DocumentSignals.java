package com.ragadmin.server.document.parser;

public record DocumentSignals(
        boolean repeatedHeaderDetected,
        boolean repeatedFooterDetected,
        boolean tooManyBlankLines,
        boolean weakParagraphStructure,
        boolean ocrNoiseDetected,
        boolean symbolDensityHigh,
        boolean tocOutlineMissing,
        boolean markdownTableDetected,
        boolean markdownImageDetected,
        boolean markdownHeadingDetected,
        double tableRatio,
        double imageRatio,
        double tableRatioThreshold,
        double imageRatioThreshold
) {

    public static DocumentSignals empty() {
        return new DocumentSignals(false, false, false, false, false, false, false,
                false, false, false, 0.0, 0.0, 0.1, 0.05);
    }

    public boolean containsTable() {
        return markdownTableDetected || tableRatio > tableRatioThreshold;
    }

    public boolean containsImage() {
        return markdownImageDetected || imageRatio > imageRatioThreshold;
    }

    public String inferContentType() {
        boolean hasTable = containsTable();
        boolean hasImage = containsImage();
        if (hasTable && hasImage) return "MIXED";
        if (hasTable) return "TABLE";
        if (hasImage) return "IMAGE";
        return "TEXT";
    }
}
