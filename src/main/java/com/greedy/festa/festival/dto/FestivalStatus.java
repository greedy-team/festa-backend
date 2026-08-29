package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.exception.FestaException;

public enum FestivalStatus {
    UPCOMING,
    ONGOING,
    ENDED;

    public static FestivalStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_STATUS_TYPE);
        }
    }

    public static String nameOrNull(FestivalStatus status) {
        if (status == null) {
            return null;
        }
        return status.name();
    }
}
