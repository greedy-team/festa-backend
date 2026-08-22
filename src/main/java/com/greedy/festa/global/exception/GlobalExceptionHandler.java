package com.greedy.festa.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FestaException.class)
    public ResponseEntity<ErrorResponse> handleFestaException(
            FestaException e, HttpServletRequest request
    ) {

        log.warn("{} - {}", e.getErrorCode().name(), e.getMessage());
        return toResponse(e.getErrorCode(), e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e, HttpServletRequest request) {

        log.error("예상하지 못한 예외", e);
        return toResponse(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpServletRequest request) {

        return toResponse(
                CommonErrorCode.PAYLOAD_TOO_LARGE,
                CommonErrorCode.PAYLOAD_TOO_LARGE.getMessage(),
                request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(
            MissingServletRequestPartException e, HttpServletRequest request) {

        return toResponse(
                CommonErrorCode.INVALID_REQUEST_BODY,
                CommonErrorCode.INVALID_REQUEST_BODY.getMessage(),
                request);
    }

    private ResponseEntity<ErrorResponse> toResponse(
            ErrorCode errorCode, String message, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
                errorCode.name(),
                message,
                errorCode.getStatus().value(),
                request.getRequestURI());

        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }
}
