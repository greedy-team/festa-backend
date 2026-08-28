package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.Festival;

import java.time.LocalDate;

public record FestivalUpcomingResponse(
        Long festivalId, String name, HostSummaryResponse host,
        String posterUrl, String venueName, LocalDate startDate, LocalDate endDate
) {

    public static FestivalUpcomingResponse from(Festival festival) {
        return new FestivalUpcomingResponse(
                festival.getId(), festival.getName(), HostSummaryResponse.from(festival.getHost()),
                festival.getPosterUrl(), festival.getVenueName(),
                festival.getStartDate(), festival.getEndDate()
        );
    }
}
