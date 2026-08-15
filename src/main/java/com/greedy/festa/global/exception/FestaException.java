package com.greedy.festa.global.exception;

import lombok.Getter;

@Getter
public class FestaException extends RuntimeException {

    private final ErrorCode errorCode;

    public FestaException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public FestaException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
