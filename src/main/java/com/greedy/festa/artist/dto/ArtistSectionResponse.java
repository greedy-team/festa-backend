package com.greedy.festa.artist.dto;

import java.util.List;

public record ArtistSectionResponse<T>(
        List<T> items,
        long total
) {
}
