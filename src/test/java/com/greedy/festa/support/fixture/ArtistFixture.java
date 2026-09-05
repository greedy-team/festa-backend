package com.greedy.festa.support.fixture;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;

public final class ArtistFixture {

    public static final ArtistGenre GENRE = ArtistGenre.BAND;

    private ArtistFixture() {
    }

    public static Artist.ArtistBuilder artist(String name) {
        return Artist.builder()
                .name(name)
                .genre(GENRE);
    }

    public static ArtistAlias.ArtistAliasBuilder alias(Artist artist, String name) {
        return ArtistAlias.builder()
                .artist(artist)
                .name(name);
    }
}
