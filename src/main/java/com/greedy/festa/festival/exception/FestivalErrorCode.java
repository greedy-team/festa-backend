package com.greedy.festa.festival.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FestivalErrorCode implements ErrorCode {

    FESTIVAL_INVALID_SORT_TYPE("지원하지 않는 정렬 기준입니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_COVERAGE_INVALID_STATUS(
            "status는 PUBLISHED, REVIEW_PENDING 또는 NEEDS_CHECK만 사용할 수 있습니다.",
            HttpStatus.BAD_REQUEST
    ),
    FESTIVAL_COVERAGE_INVALID_YEAR(
            "year는 2026년부터 현재 연도의 다음 연도까지만 사용할 수 있습니다.",
            HttpStatus.BAD_REQUEST
    ),
    FESTIVAL_PUBLISH_LINEUP_EMPTY("라인업이 없어 발행할 수 없습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_PUBLISH_HOST_NOT_LINKED("주최가 연결되지 않아 발행할 수 없습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_PUBLISH_COORDINATES_MISSING("좌표가 없어 발행할 수 없습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_NOT_FOUND("존재하지 않는 축제입니다", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
