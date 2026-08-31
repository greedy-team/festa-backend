package com.greedy.festa.search.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SearchErrorCode implements ErrorCode {

    SEARCH_INVALID_QUERY("검색어가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    SEARCH_INVALID_TYPE("지원하지 않는 검색 유형입니다", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus status;
}
