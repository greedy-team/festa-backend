package com.greedy.festa.global.exception;

public record ErrorResponse(
        String errorCode,
        String message,
        int status,
        String instance
) {
}
