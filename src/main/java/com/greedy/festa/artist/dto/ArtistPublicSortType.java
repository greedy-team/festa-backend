package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.global.exception.FestaException;

public enum ArtistPublicSortType {
    APPEARANCES,
    NAME;

    public static ArtistPublicSortType from(String value) {
        if (value == null || value.isBlank()) {
            return APPEARANCES;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_SORT_TYPE);
        }
    }
}
