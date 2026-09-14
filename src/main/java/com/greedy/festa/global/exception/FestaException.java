package com.greedy.festa.global.exception;

import lombok.Getter;

@Getter
public class FestaException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 로그에만 남는 도메인 맥락. 응답 본문의 message는 언제나 에러 코드의 문구다. */
    private final String logMessage;

    public FestaException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public FestaException(ErrorCode errorCode, String logMessage) {
        this(errorCode, logMessage, null);
    }

    public FestaException(ErrorCode errorCode, String logMessage, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.logMessage = logMessage;
    }
}
