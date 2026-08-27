package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.entity.ArtistGenre;

public record ArtistListItemResponse(
        Long artistId,
        String name,
        String imageUrl,
        ArtistGenre genre,
        long appearanceCount,
        RecentFestivalResponse recentFestival
) {
}
