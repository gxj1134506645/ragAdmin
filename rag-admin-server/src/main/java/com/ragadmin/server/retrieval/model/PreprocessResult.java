package com.ragadmin.server.retrieval.model;

public class PreprocessResult {

    private final String query;
    private final boolean modified;
    private final boolean blocked;

    public PreprocessResult(String query, boolean modified, boolean blocked) {
        this.query = query;
        this.modified = modified;
        this.blocked = blocked;
    }

    public static PreprocessResult unchanged(String query) {
        return new PreprocessResult(query, false, false);
    }

    public String getQuery() {
        return query;
    }

    public boolean isModified() {
        return modified;
    }

    public boolean isBlocked() {
        return blocked;
    }
}
