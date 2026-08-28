package com.greedy.festa.artist.entity;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.global.exception.FestaException;

public enum ArtistGenre {
    HIPHOP,
    BALLAD_RNB,
    DANCE,
    BAND;

    public static ArtistGenre from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_GENRE_TYPE);
        }
    }
}
