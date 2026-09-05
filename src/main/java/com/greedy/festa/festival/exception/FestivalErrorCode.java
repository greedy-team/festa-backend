package com.greedy.festa.festival.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FestivalErrorCode implements ErrorCode {

    FESTIVAL_INVALID_NAME("축제 이름이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_START_DATE("시작일이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_END_DATE("종료일이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_HOST_ID("주최가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_SORT_TYPE("지원하지 않는 정렬 기준입니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_IDS("festivalIds는 1개 이상 100개 이하여야 하며 null을 담을 수 없습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_LIMIT("조회 개수가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_FILTER("조회 조건의 형식이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_STATUS_TYPE("지원하지 않는 진행 상태입니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_INVALID_QUERY("검색어가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_PERIOD_CONFLICTS_LINEUP("축제 기간 밖으로 벗어나는 라인업이 있습니다", HttpStatus.CONFLICT),
    FESTIVAL_DUPLICATE_IMPORT_KEY("이미 등록된 import_key입니다", HttpStatus.CONFLICT),
    FESTIVAL_HAS_LINEUPS("라인업이 등록된 축제는 삭제할 수 없습니다", HttpStatus.CONFLICT),
    FESTIVAL_ALREADY_PUBLISHED("발행된 축제는 삭제할 수 없습니다. 발행을 해제한 뒤 삭제하세요", HttpStatus.CONFLICT),
    FESTIVAL_PUBLISHED_COORDINATES_REQUIRED(
            "발행된 축제는 좌표를 비울 수 없습니다. 발행을 해제한 뒤 수정하세요",
            HttpStatus.CONFLICT
    ),
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
    FESTIVAL_PUBLISH_ADMISSION_UNKNOWN("입장 정책에 미지값이 있어 발행할 수 없습니다", HttpStatus.BAD_REQUEST),
    FESTIVAL_NOT_FOUND("존재하지 않는 축제입니다", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
