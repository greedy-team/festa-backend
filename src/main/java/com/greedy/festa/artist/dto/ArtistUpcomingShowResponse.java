package com.greedy.festa.artist.dto;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.lineup.entity.Lineup;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ArtistUpcomingShowResponse(
        Long festivalId,
        String name,
        String hostName,
        String venueName,
        String posterUrl,
        LocalDate startDate,
        LocalDate endDate,
        long dday,
        LocalDate performanceDate,
        int day
) {

    public static ArtistUpcomingShowResponse from(Lineup lineup, LocalDate today) {
        Festival festival = lineup.getFestival();
        LocalDate performanceDate = festival.getStartDate().plusDays(lineup.getDay() - 1L);
        return new ArtistUpcomingShowResponse(
                festival.getId(), festival.getName(), festival.getHost().getName(),
                festival.getVenueName(), festival.getPosterUrl(), festival.getStartDate(),
                festival.getEndDate(), ChronoUnit.DAYS.between(today, performanceDate),
                performanceDate, lineup.getDay());
    }
}
