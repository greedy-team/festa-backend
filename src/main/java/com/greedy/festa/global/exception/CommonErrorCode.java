package com.greedy.festa.global.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
    
    INVALID_PAGE("페이지 번호가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    INVALID_PAGE_SIZE("페이지 크기가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE("기간이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    INVALID_IMAGE_FORMAT("지원하지 않는 이미지 형식입니다", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST_BODY("요청 본문을 읽을 수 없습니다", HttpStatus.BAD_REQUEST),
    INVALID_PATH_VARIABLE("경로 변수의 형식이 올바르지 않습니다", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED("인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("토큰이 만료되었습니다", HttpStatus.UNAUTHORIZED),

    FORBIDDEN("접근 권한이 없습니다", HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND("존재하지 않는 경로입니다", HttpStatus.NOT_FOUND),

    METHOD_NOT_ALLOWED("지원하지 않는 HTTP 메서드입니다", HttpStatus.METHOD_NOT_ALLOWED),

    PAYLOAD_TOO_LARGE("업로드 가능한 파일 크기를 초과했습니다", HttpStatus.CONTENT_TOO_LARGE),

    INTERNAL_SERVER_ERROR("서버 내부 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;
}
