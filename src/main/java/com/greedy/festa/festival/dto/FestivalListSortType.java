package com.greedy.festa.festival.dto;

import org.springframework.data.domain.Sort;

public enum FestivalListSortType {
    LATEST,
    UPCOMING;

    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "publishedAt").and(ID_ASC);
            case UPCOMING -> Sort.by(Sort.Direction.ASC, "startDate").and(ID_ASC);
        };
    }
}
