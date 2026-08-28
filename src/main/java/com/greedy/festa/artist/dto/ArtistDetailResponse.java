package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.entity.ArtistGenre;

import java.util.List;

public record ArtistDetailResponse(
        Long id,
        String name,
        List<String> otherNames,
        ArtistGenre genre,
        String imageUrl,
        String instagramUrl,
        ArtistSectionResponse<ArtistUpcomingShowResponse> upcomingShows,
        ArtistSectionResponse<ArtistAppearanceResponse> appearances
) {
}
