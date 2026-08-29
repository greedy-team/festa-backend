package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.exception.FestaException;
import org.springframework.data.domain.Sort;

public enum FestivalListSortType {
    LATEST,
    UPCOMING;

    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    public static FestivalListSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_SORT_TYPE);
        }
    }

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "publishedAt").and(ID_ASC);
            case UPCOMING -> Sort.by(Sort.Direction.ASC, "startDate").and(ID_ASC);
        };
    }
}
