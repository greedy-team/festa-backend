package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.repository.ArtistRecentFestivalRow;

public record RecentFestivalResponse(
        Long festivalId,
        String name,
        String hostShortName
) {

    public static RecentFestivalResponse from(ArtistRecentFestivalRow row) {
        return new RecentFestivalResponse(
                row.getFestivalId(), row.getFestivalName(), row.getHostShortName());
    }
}
