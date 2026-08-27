package com.greedy.festa.artist.dto;

public record RecentFestivalResponse(
        Long festivalId,
        String name,
        String hostShortName
) {
}
