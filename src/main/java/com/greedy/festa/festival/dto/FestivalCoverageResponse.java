package com.greedy.festa.festival.dto;

import java.util.List;

public record FestivalCoverageResponse(
        int year,
        FestivalCoverageSummary summary,
        List<FestivalCoverageItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
