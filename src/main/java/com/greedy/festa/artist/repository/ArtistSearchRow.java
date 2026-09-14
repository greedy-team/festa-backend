package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.entity.Artist;

import java.time.LocalDate;

public interface ArtistSearchRow {
    Artist getArtist();
    Long getAppearanceCount();
    LocalDate getLatestAppearanceDate();
}
