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
    IMPORT_INVALID_STATUS("지원하지 않는 임포트 상태입니다", HttpStatus.BAD_REQUEST),
    IMPORT_INVALID_CONFLICT_POLICY("지원하지 않는 충돌 정책입니다", HttpStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE("업로드 가능한 파일 크기를 초과했습니다", HttpStatus.CONTENT_TOO_LARGE),
    IMPORT_NOT_FOUND("임포트 배치를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    IMPORT_ALREADY_COMMITTED("이미 커밋된 임포트 배치입니다", HttpStatus.CONFLICT),
    IMPORT_EXPIRED("만료된 임포트 미리보기입니다", HttpStatus.GONE),
    IMPORT_UNSUPPORTED_PREVIEW_VERSION("지원하지 않는 미리보기 버전입니다", HttpStatus.CONFLICT),
    IMPORT_INVALID_PREVIEW("저장된 미리보기를 읽을 수 없습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    IMPORT_INVALID_LINE_SELECTION("커밋 행 선택이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    IMPORT_UNCOMMITTABLE("선택한 행을 커밋할 수 없습니다", HttpStatus.CONFLICT),
    IMPORT_PREVIEW_STALE("미리보기 이후 데이터가 변경되었습니다", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus status;
}
