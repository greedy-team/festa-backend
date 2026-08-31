package com.greedy.festa.search.dto;

import com.greedy.festa.artist.repository.ArtistSearchRow;

import java.time.LocalDate;

public record SearchArtistResponse(
        Long artistId,
        String name,
        String imageUrl,
        long appearanceCount,
        LocalDate latestAppearanceDate
) {

    public static SearchArtistResponse from(ArtistSearchRow row) {
        return new SearchArtistResponse(
                row.getArtist().getId(),
                row.getArtist().getName(),
                null,
                row.getAppearanceCount(),
                row.getLatestAppearanceDate()
        );
    }
}
