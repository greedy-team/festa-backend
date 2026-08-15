package com.greedy.festa.host.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HostErrorCode implements ErrorCode {

    HOST_INVALID_NAME("주최 이름이 올바르지 않습니다",HttpStatus.BAD_REQUEST),
    HOST_INVALID_REGION("지역이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    HOST_DUPLICATE_NAME("이미 등록된 주최 이름입니다", HttpStatus.CONFLICT),
    HOST_HAS_FESTIVALS("축제가 등록된 주최는 삭제할 수 없습니다", HttpStatus.CONFLICT),
    HOST_NOT_FOUND("존재하지 않는 주최입니다", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
