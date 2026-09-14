package com.greedy.festa.festival.dto;

import com.greedy.festa.host.repository.HostCoverageRow;

import java.time.LocalDate;

public record FestivalCoverageItem(
        Long hostId,
        Long festivalId,
        String hostName,
        String festivalName,
        LocalDate startDate,
        LocalDate endDate,
        FestivalCoverageStatus status,
        String instagramUrl
) {

    public static FestivalCoverageItem of(
            HostCoverageRow row, FestivalCoverageStatus status
    ) {
        return new FestivalCoverageItem(
                row.getHostId(),
                row.getFestivalId(),
                row.getHostName(),
                row.getFestivalName(),
                row.getStartDate(),
                row.getEndDate(),
                status,
                row.getInstagramUrl()
        );
    }
}
