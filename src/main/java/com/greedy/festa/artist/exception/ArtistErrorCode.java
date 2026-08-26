package com.greedy.festa.artist.exception;

import com.greedy.festa.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ArtistErrorCode implements ErrorCode {

    ARTIST_INVALID_NAME("아티스트 이름이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    ARTIST_INVALID_ALIAS("아티스트 별칭이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    ARTIST_INVALID_QUERY("검색어가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    ARTIST_INVALID_GENRE_TYPE("지원하지 않는 장르입니다", HttpStatus.BAD_REQUEST),
    ARTIST_INVALID_SORT_TYPE("지원하지 않는 정렬 기준입니다", HttpStatus.BAD_REQUEST),
    ARTIST_INVALID_TARGET_ID("남길 아티스트가 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    ARTIST_INVALID_SOURCE_IDS("병합 대상 아티스트 목록이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    ARTIST_SELF_MERGE("자기 자신과는 병합할 수 없습니다", HttpStatus.BAD_REQUEST),
    ARTIST_DUPLICATE_NAME("이미 등록된 아티스트 이름입니다", HttpStatus.CONFLICT),
    ARTIST_DUPLICATE_ALIAS("이미 사용 중인 아티스트 별칭입니다", HttpStatus.CONFLICT),
    ARTIST_HAS_APPEARANCES("출연 이력이 있는 아티스트는 삭제할 수 없습니다", HttpStatus.CONFLICT),
    ARTIST_NOT_FOUND("존재하지 않는 아티스트입니다", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
