package com.ragadmin.server.retrieval.config;

public enum QueryRewritingMode {

    NONE,
    MULTI_QUERY,
    HYDE,
    MULTI_QUERY_AND_HYDE;

    public static QueryRewritingMode resolve(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
