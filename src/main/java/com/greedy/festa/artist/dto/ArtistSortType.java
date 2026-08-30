package com.greedy.festa.artist.dto;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.global.util.EnumParser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

public enum ArtistSortType {

    CREATED_DESC,
    APPEARANCES,
    NAME;

    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    public static ArtistSortType from(String value) {
        return EnumParser.parse(
                ArtistSortType.class, value,
                ArtistSortType.CREATED_DESC, ArtistErrorCode.ARTIST_INVALID_SORT_TYPE
        );
    }

    public Sort toSort() {
        return switch (this) {
            case CREATED_DESC -> Sort.by(Sort.Direction.DESC, "createdAt").and(ID_ASC);
            case APPEARANCES -> JpaSort.unsafe(Sort.Direction.DESC, "appearanceCount").and(ID_ASC);
            case NAME -> Sort.by(Sort.Direction.ASC, "name").and(ID_ASC);
        };
    }
}
