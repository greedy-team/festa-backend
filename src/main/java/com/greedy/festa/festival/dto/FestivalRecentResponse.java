package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.Festival;

import java.time.LocalDate;

public record FestivalRecentResponse(
        Long festivalId, String name, HostSummaryResponse host,
        String posterUrl, LocalDate startDate, LocalDate endDate
) {

    public static FestivalRecentResponse from(Festival festival) {
        return new FestivalRecentResponse(
                festival.getId(), festival.getName(), HostSummaryResponse.from(festival.getHost()),
                festival.getPosterUrl(), festival.getStartDate(), festival.getEndDate()
        );
    }
}
