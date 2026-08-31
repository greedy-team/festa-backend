package com.greedy.festa.artist.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LineupErrorCode implements ErrorCode {
    LINEUP_INVALID_DAY("라인업 일차가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    LINEUP_INVALID_DISPLAY_ORDER("라인업 표시 순서가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    LINEUP_INVALID_ARTIST_ID("아티스트 id가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    LINEUP_NOT_FOUND("존재하지 않는 라인업입니다", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
