package com.greedy.festa.admin.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements ErrorCode {

    ADMIN_INVALID_CREDENTIALS("아이디 또는 비밀번호가 올바르지 않습니다", HttpStatus.UNAUTHORIZED);

    private final String message;
    private final HttpStatus status;
}
