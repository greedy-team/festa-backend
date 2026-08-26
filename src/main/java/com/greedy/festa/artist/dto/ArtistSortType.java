package com.greedy.festa.artist.dto;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

public enum ArtistSortType {

    CREATED_DESC,
    APPEARANCES,
    NAME;

    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    public Sort toSort() {
        return switch (this) {
            case CREATED_DESC -> Sort.by(Sort.Direction.DESC, "createdAt").and(ID_ASC);
            case APPEARANCES -> JpaSort.unsafe(Sort.Direction.DESC, "appearanceCount").and(ID_ASC);
            case NAME -> Sort.by(Sort.Direction.ASC, "name").and(ID_ASC);
        };
    }
}
