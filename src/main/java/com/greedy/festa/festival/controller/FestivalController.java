package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalListItemResponse;
import com.greedy.festa.festival.dto.FestivalListSortType;
import com.greedy.festa.festival.dto.FestivalRecentResponse;
import com.greedy.festa.festival.dto.FestivalStatus;
import com.greedy.festa.festival.dto.FestivalUpcomingResponse;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.service.FestivalService;
import com.greedy.festa.global.dto.ItemsResponse;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.ErrorCode;
import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@Tag(name = "축제", description = "축제 조회. 발행된 축제만 내려간다.")
@RestController
@RequestMapping("/api/festivals")
@RequiredArgsConstructor
public class FestivalController {

    private static final Map<String, ErrorCode> QUERY_PARAM_ERROR_CODES = Map.of(
            "limit", FestivalErrorCode.FESTIVAL_INVALID_LIMIT,
            "page", CommonErrorCode.INVALID_PAGE,
            "size", CommonErrorCode.INVALID_PAGE_SIZE,
            "hostId", FestivalErrorCode.FESTIVAL_INVALID_FILTER,
            "year", FestivalErrorCode.FESTIVAL_INVALID_FILTER,
            "artistId", FestivalErrorCode.FESTIVAL_INVALID_FILTER
    );

    private final FestivalService festivalService;
    private final GlobalExceptionHandler globalExceptionHandler;

    @Operation(summary = "축제 목록 조회",
            description = "sort는 순서만 정하고 status는 걸러낸다 — sort는 LATEST(기본) / UPCOMING, "
                    + "status는 UPCOMING / ONGOING / ENDED(한국 시간 오늘 기준, 시작일·종료일 포함)다. "
                    + "artistId를 주면 그 아티스트가 출연한 축제만 남고, q는 축제 이름 부분 일치다.")
    @ApiResponse(responseCode = "200", description = "축제 목록 페이지")
    @ApiResponse(responseCode = "400",
            description = "INVALID_PAGE / INVALID_PAGE_SIZE / FESTIVAL_INVALID_SORT_TYPE "
                    + "/ FESTIVAL_INVALID_STATUS_TYPE / FESTIVAL_INVALID_FILTER",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<FestivalListItemResponse> getFestivals(
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return festivalService.getFestivals(
                hostId, year, artistId, FestivalStatus.from(status), q,
                FestivalListSortType.from(sort), page, size
        );
    }

    @Operation(summary = "다가오는 축제 조회",
            description = "종료되지 않은 축제를 시작일 오름차순으로 준다 — 진행 중인 축제가 앞에 온다. "
                    + "종료 판정은 한국 시간의 오늘을 기준으로 한다. limit은 1~50.")
    @ApiResponse(responseCode = "200", description = "다가오는 축제 목록")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_INVALID_LIMIT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/upcoming")
    public ItemsResponse<FestivalUpcomingResponse> getUpcomingFestivals(
            @RequestParam(defaultValue = "10") int limit) {
        return ItemsResponse.of(festivalService.getUpcomingFestivals(limit));
    }

    @Operation(summary = "최근 등록된 축제 조회",
            description = "발행 시각 역순. 진행 상태로 거르지 않으므로 이미 종료된 축제도 포함된다. limit은 1~30.")
    @ApiResponse(responseCode = "200", description = "최근 등록된 축제 목록")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_INVALID_LIMIT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/recent")
    public ItemsResponse<FestivalRecentResponse> getRecentFestivals(
            @RequestParam(defaultValue = "10") int limit) {
        return ItemsResponse.of(festivalService.getRecentPublished(limit));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleQueryParamTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        ErrorCode errorCode = QUERY_PARAM_ERROR_CODES.get(e.getName());

        if (errorCode == null) {
            return globalExceptionHandler.handleMethodArgumentTypeMismatchException(e, request);
        }

        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI()));
    }
}
