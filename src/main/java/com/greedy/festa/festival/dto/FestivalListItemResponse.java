package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.Festival;

import java.time.LocalDate;

public record FestivalListItemResponse(
        Long festivalId, String name, HostSummaryResponse host,
        String posterUrl, LocalDate startDate, LocalDate endDate
) {

    public static FestivalListItemResponse from(Festival festival) {
        return new FestivalListItemResponse(
                festival.getId(), festival.getName(), HostSummaryResponse.from(festival.getHost()),
                festival.getPosterUrl(), festival.getStartDate(), festival.getEndDate()
        );
    }
}
