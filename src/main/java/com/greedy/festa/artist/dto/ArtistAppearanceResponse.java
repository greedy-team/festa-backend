package com.greedy.festa.artist.dto;

import com.greedy.festa.festival.entity.Festival;

import java.time.LocalDate;

public record ArtistAppearanceResponse(
        Long festivalId,
        String name,
        String hostName,
        LocalDate startDate,
        LocalDate endDate
) {

    public static ArtistAppearanceResponse from(Festival festival) {
        return new ArtistAppearanceResponse(
                festival.getId(), festival.getName(), festival.getHost().getName(),
                festival.getStartDate(), festival.getEndDate());
    }
}
