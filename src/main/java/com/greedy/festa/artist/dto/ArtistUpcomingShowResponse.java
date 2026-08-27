package com.greedy.festa.artist.dto;

import java.time.LocalDate;

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
}
