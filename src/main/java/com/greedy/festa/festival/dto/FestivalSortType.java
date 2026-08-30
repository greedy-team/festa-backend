package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.util.EnumParser;
import org.springframework.data.domain.Sort;

public enum FestivalSortType {

    IMPORTED_DESC,
    START_DATE;

    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    public static FestivalSortType from(String value) {
        return EnumParser.parse(
                FestivalSortType.class, value,
                IMPORTED_DESC, FestivalErrorCode.FESTIVAL_INVALID_SORT_TYPE
        );
    }

    public Sort toSort() {
        return switch (this) {
            case IMPORTED_DESC -> Sort.by(Sort.Order.desc("importedAt").nullsLast()).and(ID_ASC);
            case START_DATE -> Sort.by(Sort.Direction.ASC, "startDate").and(ID_ASC);
        };
    }
}
