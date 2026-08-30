package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.util.EnumParser;

public enum FestivalCoverageStatus {
    PUBLISHED,
    REVIEW_PENDING,
    NEEDS_CHECK;

    public static FestivalCoverageStatus from(String value) {
        return EnumParser.parse(
                FestivalCoverageStatus.class, value,
                FestivalErrorCode.FESTIVAL_COVERAGE_INVALID_STATUS
        );
    }
}
