package com.greedy.festa.importer.dto;

public record ImportHistoryResult(
        ImportHistorySectionResult artists,
        ImportHistorySectionResult festivals,
        ImportHistorySectionResult lineups
) {
}
