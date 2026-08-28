package com.greedy.festa.global.exception;

public record ErrorResponse(
        String errorCode,
        String message,
        int status,
        String instance
) {

    public static ErrorResponse of(ErrorCode errorCode, String instance) {
        return new ErrorResponse(
                errorCode.name(), errorCode.getMessage(), errorCode.getStatus().value(), instance);
    }
}
