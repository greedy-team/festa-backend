package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.repository.ArtistWithAppearanceCount;

public record ArtistListItemResponse(
        Long artistId,
        String name,
        String imageUrl,
        ArtistGenre genre,
        long appearanceCount,
        RecentFestivalResponse recentFestival
) {

    public static ArtistListItemResponse from(
            ArtistWithAppearanceCount row, RecentFestivalResponse recentFestival
    ) {
        var artist = row.getArtist();
        return new ArtistListItemResponse(
                artist.getId(), artist.getName(), null, artist.getGenre(),
                row.getAppearanceCount(), recentFestival);
    }
}
