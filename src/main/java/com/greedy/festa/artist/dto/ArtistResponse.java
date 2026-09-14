package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;

import java.time.Instant;
import java.util.List;

public record ArtistResponse(
        Long artistId, String name, List<String> otherNames, ArtistGenre genre,
        String imageUrl, String instagramUrl, Integer appearanceCount,
        Boolean needsReview, Instant createdAt
) {

    public static ArtistResponse of(Artist artist, List<String> otherNames, long appearanceCount) {
        return new ArtistResponse(
                artist.getId(), artist.getName(), otherNames, artist.getGenre(),
                artist.getImageUrl(), artist.getInstagramUrl(), (int) appearanceCount,
                artist.isNeedsReview(), artist.getCreatedAt()
        );
    }
}
