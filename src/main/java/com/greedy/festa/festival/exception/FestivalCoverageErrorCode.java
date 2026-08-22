package com.greedy.festa.festival.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FestivalCoverageErrorCode implements ErrorCode {

    FESTIVAL_COVERAGE_INVALID_STATUS(
            "status는 PUBLISHED, REVIEW_PENDING 또는 NEEDS_CHECK만 사용할 수 있습니다.",
            HttpStatus.BAD_REQUEST
    ),
    FESTIVAL_COVERAGE_INVALID_YEAR(
            "year는 2026년부터 현재 연도의 다음 연도까지만 사용할 수 있습니다.",
            HttpStatus.BAD_REQUEST
    );

    private final String message;
    private final HttpStatus status;
}
