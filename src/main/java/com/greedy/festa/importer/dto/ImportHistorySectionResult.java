package com.greedy.festa.importer.dto;

public record ImportHistorySectionResult(long created, long updated, long skipped) {

    public static ImportHistorySectionResult empty() {
        return new ImportHistorySectionResult(0, 0, 0);
    }
}
