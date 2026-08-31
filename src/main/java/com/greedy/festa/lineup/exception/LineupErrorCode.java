package com.greedy.festa.lineup.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LineupErrorCode implements ErrorCode {

    LINEUP_INVALID_DAY("일차는 1 이상의 정수여야 합니다", HttpStatus.BAD_REQUEST),
    LINEUP_INVALID_DISPLAY_ORDER("무대 순서는 1 이상의 정수여야 합니다", HttpStatus.BAD_REQUEST),
    LINEUP_DAY_OUT_OF_RANGE("일차가 축제 기간을 벗어났습니다", HttpStatus.BAD_REQUEST),
    LINEUP_DUPLICATE_SLOT("같은 일차에 이미 사용 중인 무대 순서입니다", HttpStatus.CONFLICT),
    LINEUP_NOT_FOUND("존재하지 않는 라인업입니다", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
