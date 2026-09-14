package com.greedy.festa.festival.dto;

import com.greedy.festa.global.dto.PageResponse;

public record FestivalCoverageResponse(
        int year,
        FestivalCoverageSummary summary,
        PageResponse<FestivalCoverageItem> hosts
) {
}
