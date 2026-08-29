package com.greedy.festa.festival.dto;

import org.springframework.data.domain.Sort;

public enum FestivalSortType {

    IMPORTED_DESC,
    START_DATE;

    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    public Sort toSort() {
        return switch (this) {
            case IMPORTED_DESC -> Sort.by(Sort.Order.desc("importedAt").nullsLast()).and(ID_ASC);
            case START_DATE -> Sort.by(Sort.Direction.ASC, "startDate").and(ID_ASC);
        };
    }
}
