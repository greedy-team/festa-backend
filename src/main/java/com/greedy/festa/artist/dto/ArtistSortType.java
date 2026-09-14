package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.global.util.EnumParser;

public enum ArtistSortType {
    APPEARANCES,
    NAME;

    public static ArtistSortType from(String value) {
        return EnumParser.parse(
                ArtistSortType.class, value,
                APPEARANCES, ArtistErrorCode.ARTIST_INVALID_SORT_TYPE
        );
    }
}
