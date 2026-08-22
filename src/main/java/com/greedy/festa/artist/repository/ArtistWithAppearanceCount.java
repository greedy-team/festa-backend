package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.Artist;

public interface ArtistWithAppearanceCount {
    Artist getArtist();
    Long getAppearanceCount();
}
