package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.util.EnumParser;

public enum FestivalStatus {
    UPCOMING,
    ONGOING,
    ENDED;

    public static FestivalStatus from(String value) {
        return EnumParser.parse(
                FestivalStatus.class, value,
                FestivalErrorCode.FESTIVAL_INVALID_STATUS_TYPE
        );
    }
}
