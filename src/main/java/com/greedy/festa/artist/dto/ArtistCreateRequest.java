package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.entity.ArtistGenre;

import java.util.List;

public record ArtistCreateRequest(
        String name, List<String> otherNames, ArtistGenre genre, String instagramUrl
) {
}
