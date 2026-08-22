package com.greedy.festa.importer.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImportErrorCode implements ErrorCode {

    IMPORT_EMPTY_CSV("CSV 파일이 비어 있습니다", HttpStatus.BAD_REQUEST),
    IMPORT_INVALID_CSV_ENCODING("CSV 파일은 UTF-8이어야 합니다", HttpStatus.BAD_REQUEST),
    IMPORT_INVALID_CSV_HEADER("CSV 헤더가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    IMPORT_INVALID_CSV("CSV 파일을 읽을 수 없습니다", HttpStatus.BAD_REQUEST),
    IMPORT_MISSING_FILE("필수 CSV 파일이 없습니다", HttpStatus.BAD_REQUEST),
    IMPORT_INVALID_TYPE("지원하지 않는 임포트 유형입니다", HttpStatus.BAD_REQUEST),
    IMPORT_INVALID_CONFLICT_POLICY("지원하지 않는 충돌 정책입니다", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus status;
}
