package com.greedy.festa.search.dto;

import com.greedy.festa.festival.dto.HostSummaryResponse;
import com.greedy.festa.festival.entity.Festival;

import java.time.LocalDate;

public record SearchFestivalResponse(
        Long festivalId,
        String name,
        HostSummaryResponse host,
        LocalDate startDate,
        LocalDate endDate,
        String posterUrl
) {

    public static SearchFestivalResponse from(Festival festival) {
        return new SearchFestivalResponse(
                festival.getId(),
                festival.getName(),
                HostSummaryResponse.from(festival.getHost()),
                festival.getStartDate(),
                festival.getEndDate(),
                festival.getPosterUrl()
        );
    }
}
