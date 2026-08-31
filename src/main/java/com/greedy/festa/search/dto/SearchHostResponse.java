package com.greedy.festa.search.dto;

import com.greedy.festa.host.repository.HostSearchRow;

import java.time.YearMonth;

public record SearchHostResponse(
        Long hostId,
        String name,
        String logoUrl,
        long festivalCount,
        String latestFestivalYearMonth
) {

    public static SearchHostResponse from(HostSearchRow row) {
        return new SearchHostResponse(
                row.getHost().getId(),
                row.getHost().getName(),
                row.getHost().getLogoUrl(),
                row.getFestivalCount(),
                row.getLatestFestivalDate() == null
                        ? null : YearMonth.from(row.getLatestFestivalDate()).toString()
        );
    }
}
