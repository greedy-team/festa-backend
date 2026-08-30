package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.global.util.EnumParser;

public enum ArtistPublicSortType {
    APPEARANCES,
    NAME;

    public static ArtistPublicSortType from(String value) {
        return EnumParser.parse(
                ArtistPublicSortType.class, value,
                APPEARANCES, ArtistErrorCode.ARTIST_INVALID_SORT_TYPE
        );
    }
}
