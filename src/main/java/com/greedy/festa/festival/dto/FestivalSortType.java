package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.util.EnumParser;
import org.springframework.data.domain.Sort;

public enum FestivalSortType {
    LATEST,
    UPCOMING;

    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    public static FestivalSortType from(String value) {
        return EnumParser.parse(
                FestivalSortType.class, value,
                LATEST, FestivalErrorCode.FESTIVAL_INVALID_SORT_TYPE
        );
    }

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "publishedAt").and(ID_ASC);
            case UPCOMING -> Sort.by(Sort.Direction.ASC, "startDate").and(ID_ASC);
        };
    }
}
