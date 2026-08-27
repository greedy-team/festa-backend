package com.greedy.festa.artist.dto;

import java.time.LocalDate;

public record ArtistAppearanceResponse(
        Long festivalId,
        String name,
        String hostName,
        LocalDate startDate,
        LocalDate endDate
) {
}
