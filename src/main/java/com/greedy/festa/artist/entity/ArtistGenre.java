package com.greedy.festa.artist.entity;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.global.util.EnumParser;

public enum ArtistGenre {
    HIPHOP,
    BALLAD_RNB,
    DANCE,
    BAND;

    public static ArtistGenre from(String value) {
        return EnumParser.parse(
                ArtistGenre.class, value,
                ArtistErrorCode.ARTIST_INVALID_GENRE_TYPE
        );
    }
}
