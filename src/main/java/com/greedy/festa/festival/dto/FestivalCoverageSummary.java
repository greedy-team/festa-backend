package com.greedy.festa.festival.dto;

public record FestivalCoverageSummary(
        long totalHosts,
        long published,
        long reviewPending,
        long needsCheck,
        int coverageRate
) {
}
