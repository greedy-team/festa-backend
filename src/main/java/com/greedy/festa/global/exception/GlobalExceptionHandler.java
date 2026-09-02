package com.greedy.festa.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // page·size는 도메인과 무관하게 뜻이 같아 전역에서 400으로 가른다.
    private static final Map<String, ErrorCode> COMMON_QUERY_PARAM_ERROR_CODES = Map.of(
            "page", CommonErrorCode.INVALID_PAGE,
            "size", CommonErrorCode.INVALID_PAGE_SIZE
    );

    @ExceptionHandler(FestaException.class)
    public ResponseEntity<ErrorResponse> handleFestaException(
            FestaException e, HttpServletRequest request
    ) {

        if (e.getCause() == null) {
            log.warn("{} - {} {}{}", e.getErrorCode().name(),
                    request.getMethod(), request.getRequestURI(), logContext(e.getLogMessage()));
        } else {
            log.warn("{} - {} {}{}", e.getErrorCode().name(),
                    request.getMethod(), request.getRequestURI(), logContext(e.getLogMessage()),
                    e.getCause());
        }
        return toResponse(e.getErrorCode(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e, HttpServletRequest request) {

        log.error("{} - {} {}", CommonErrorCode.INTERNAL_SERVER_ERROR.name(),
                request.getMethod(), request.getRequestURI(), e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpServletRequest request) {

        log.warn("{} - {} {} (한도={}바이트)", CommonErrorCode.PAYLOAD_TOO_LARGE.name(),
                request.getMethod(), request.getRequestURI(), e.getMaxUploadSize());
        return toResponse(CommonErrorCode.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(
            MissingServletRequestPartException e, HttpServletRequest request) {

        log.warn("{} - {} {} (누락 파트={})", CommonErrorCode.INVALID_REQUEST_BODY.name(),
                request.getMethod(), request.getRequestURI(), e.getRequestPartName());
        return toResponse(CommonErrorCode.INVALID_REQUEST_BODY, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        if (e.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return toTypeMismatchResponse(CommonErrorCode.INVALID_PATH_VARIABLE, e, request);
        }

        // page·size 밖의 쿼리 파라미터는 도메인마다 에러 코드가 갈려(limit이 축제에선
        // FESTIVAL_INVALID_LIMIT, 아티스트에선 ARTIST_INVALID_LIMIT) 전역에서 정할 수 없다.
        // 컨트롤러 로컬 @ExceptionHandler가 맡고, 맡는 곳이 없으면 여기서 500으로 남는다.
        ErrorCode queryParamErrorCode = COMMON_QUERY_PARAM_ERROR_CODES.get(e.getName());
        if (queryParamErrorCode != null) {
            return toTypeMismatchResponse(queryParamErrorCode, e, request);
        }

        // 이 예외는 컨트롤러 메서드 진입 전 인자 바인딩 단계에서 던져져 스택트레이스에
        // 컨트롤러가 올라오지 않는다. 요청 정보를 함께 남겨야 어느 API인지 특정할 수 있다.
        log.error("{} - {} {} (param={}, value={})",
                CommonErrorCode.INTERNAL_SERVER_ERROR.name(),
                request.getMethod(), request.getRequestURI(), e.getName(), e.getValue(), e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ErrorResponse> toTypeMismatchResponse(
            ErrorCode errorCode, MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        log.warn("{} - {} {} (param={}, value={})",
                errorCode.name(),
                request.getMethod(), request.getRequestURI(), e.getName(), e.getValue());
        return toResponse(errorCode, request);
    }

    // 메시지를 인자로 받지 않는다. 도메인 맥락이 응답으로 흘러갈 경로를 없애기 위해서다.
    private ResponseEntity<ErrorResponse> toResponse(
            ErrorCode errorCode, HttpServletRequest request) {

        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }

    private String logContext(String logMessage) {
        if (logMessage == null) {
            return "";
        }
        return " - " + logMessage;
    }
}
