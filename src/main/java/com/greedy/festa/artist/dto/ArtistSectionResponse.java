package com.greedy.festa.artist.dto;

import java.util.List;

public record ArtistSectionResponse<T>(
        List<T> items,
        long total
) {

    public static <T> ArtistSectionResponse<T> from(List<T> all, int maxItems) {
        return new ArtistSectionResponse<>(
                all.stream().limit(maxItems).toList(), all.size());
    }
}
