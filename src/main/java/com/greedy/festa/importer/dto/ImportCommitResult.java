package com.greedy.festa.importer.dto;

public record ImportCommitResult(
        ImportCommitSectionResult artists,
        ImportCommitSectionResult festivals,
        ImportCommitSectionResult lineups
) {
}
