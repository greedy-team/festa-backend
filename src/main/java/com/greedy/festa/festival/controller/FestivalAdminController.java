package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalBatchPublishRequest;
import com.greedy.festa.festival.dto.FestivalBatchPublishResponse;
import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.dto.FestivalCreateRequest;
import com.greedy.festa.festival.dto.FestivalPublishResponse;
import com.greedy.festa.festival.dto.FestivalResponse;
import com.greedy.festa.festival.dto.FestivalReviewItem;
import com.greedy.festa.festival.dto.FestivalSortType;
import com.greedy.festa.festival.dto.FestivalUpdateRequest;
import com.greedy.festa.festival.service.FestivalAdminService;
import com.greedy.festa.festival.service.FestivalPublishService;
import com.greedy.festa.festival.service.FestivalCoverageService;
import com.greedy.festa.global.config.SwaggerConfig;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 축제", description = "축제 등록·조회·수정·삭제와 검수 목록, 발행·발행 취소, 주최별 데이터 구축 현황. "
        + "토큰이 없거나 만료되면 401(UNAUTHORIZED / TOKEN_EXPIRED)이다.")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/festivals")
@RequiredArgsConstructor
public class FestivalAdminController {

    private final FestivalAdminService festivalAdminService;
    private final FestivalPublishService festivalPublishService;
    private final FestivalCoverageService festivalCoverageService;

    @Operation(summary = "축제 검수 목록 조회",
            description = "q는 축제 이름을 찾고, sort는 IMPORTED_DESC(기본) / START_DATE다. "
                    + "각 항목의 blockers는 지금 발행을 막고 있는 사유다.")
    @ApiResponse(responseCode = "200", description = "검수 항목 페이지")
    @ApiResponse(responseCode = "400", description = "INVALID_PAGE / INVALID_PAGE_SIZE / FESTIVAL_INVALID_SORT_TYPE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<FestivalReviewItem> findAll(
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String discovery,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return festivalPublishService.findAll(
                published, hostId, year, q, discovery,
                FestivalSortType.from(sort),
                page, size
        );
    }

    @Operation(summary = "축제 단건 조회", description = "관리자 수정·검수에 필요한 축제의 현재 값을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "축제 상세")
    @ApiResponse(responseCode = "404", description = "FESTIVAL_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public FestivalResponse findOne(@PathVariable Long id) {
        return festivalAdminService.findOne(id);
    }

    @Operation(summary = "축제 등록",
            description = "필수는 name·startDate·endDate·hostId다. 나머지는 비워도 저장되고 발행 게이트가 막는다. "
                    + "좌표는 크롤러 시드가 원본이지만 손으로 만든 축제는 여기서만 채울 수 있다.")
    @ApiResponse(responseCode = "201", description = "등록된 축제")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_INVALID_NAME / FESTIVAL_INVALID_START_DATE "
            + "/ FESTIVAL_INVALID_END_DATE / FESTIVAL_INVALID_HOST_ID / INVALID_DATE_RANGE "
            + "/ INVALID_REQUEST_BODY - UNKNOWN 입장 정책을 요청한 경우",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "HOST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "FESTIVAL_DUPLICATE_IMPORT_KEY",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<FestivalResponse> create(@RequestBody FestivalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(festivalAdminService.create(request));
    }

    @Operation(summary = "축제 수정",
            description = "전체 교체다 - name·startDate·endDate·hostId는 필수이며 생략·null(name은 공백도 포함)이면 400이다. "
                    + "importKey·posterUrl·description·venueName·address·admissionNote·instagramUrl과 "
                    + "latitude·longitude·externalVisitor·verification·ticketType·ticketOpenAt은 "
                    + "생략하거나 null이면 삭제된다(문자열은 공백도 삭제로 읽는다). "
                    + "현재 값을 먼저 조회해 채운 뒤 통째로 보낸다. 발행된 축제도 수정할 수 있으나, "
                    + "발행 조건인 좌표(latitude·longitude)는 비울 수 없다.")
    @ApiResponse(responseCode = "200", description = "수정된 축제")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_INVALID_NAME / FESTIVAL_INVALID_START_DATE "
            + "/ FESTIVAL_INVALID_END_DATE / FESTIVAL_INVALID_HOST_ID / INVALID_DATE_RANGE "
            + "/ INVALID_REQUEST_BODY - UNKNOWN 입장 정책을 요청한 경우",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "FESTIVAL_NOT_FOUND / HOST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "FESTIVAL_DUPLICATE_IMPORT_KEY "
            + "/ FESTIVAL_PERIOD_CONFLICTS_LINEUP - 기간을 줄이면 기존 라인업이 밖으로 나가는 경우 "
            + "/ FESTIVAL_PUBLISHED_COORDINATES_REQUIRED - 발행된 축제의 좌표를 비우는 경우",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}")
    public FestivalResponse update(@PathVariable Long id, @RequestBody FestivalUpdateRequest request) {
        return festivalAdminService.update(id, request);
    }

    @Operation(summary = "축제 삭제",
            description = "발행 중이거나 라인업이 남아 있으면 지우지 않는다. 발행 해제와 라인업 삭제가 선행되어야 한다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @ApiResponse(responseCode = "404", description = "FESTIVAL_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "FESTIVAL_ALREADY_PUBLISHED / FESTIVAL_HAS_LINEUPS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        festivalAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "대시보드 - 주최별 축제 데이터 구축 현황",
            description = "year를 생략하면 올해다. status는 PUBLISHED / REVIEW_PENDING / NEEDS_CHECK이며, "
                    + "생략하면 PUBLISHED를 뺀 나머지만 내려간다.")
    @ApiResponse(responseCode = "200", description = "연도·요약·주최별 현황")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_COVERAGE_INVALID_YEAR / FESTIVAL_COVERAGE_INVALID_STATUS "
            + "/ INVALID_PAGE / INVALID_PAGE_SIZE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/coverage")
    public FestivalCoverageResponse findCoverage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return festivalCoverageService.findCoverage(year, status, page, size);
    }

    @Operation(summary = "축제 일괄 발행",
            description = "festivalIds는 1~100개다. 부분 성공을 허용해 발행된 id는 publishedIds에, 막힌 id는 "
                    + "failed[]에 사유와 함께 담긴다. reason은 LINEUP_EMPTY / HOST_NOT_LINKED / "
                    + "COORDINATES_MISSING / ADMISSION_UNKNOWN / NOT_FOUND이며 에러 코드와는 다른 체계다. "
                    + "없는 id도 전체를 막지 않고, 이미 발행된 축제에 다시 요청해도 오류가 아니다.")
    @ApiResponse(responseCode = "200", description = "발행된 id 목록과 실패 목록")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_INVALID_IDS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/publish")
    public FestivalBatchPublishResponse batchPublish(@RequestBody FestivalBatchPublishRequest request) {
        return festivalPublishService.batchPublish(request.festivalIds());
    }

    @Operation(summary = "축제 발행",
            description = "라인업·주최·좌표가 모두 있어야 발행된다. 이미 발행된 축제에 다시 요청해도 오류가 아니다.")
    @ApiResponse(responseCode = "200", description = "발행 상태")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_PUBLISH_LINEUP_EMPTY / FESTIVAL_PUBLISH_HOST_NOT_LINKED "
            + "/ FESTIVAL_PUBLISH_COORDINATES_MISSING / FESTIVAL_PUBLISH_ADMISSION_UNKNOWN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "FESTIVAL_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{id}/publish")
    public FestivalPublishResponse publish(@PathVariable Long id) {
        return festivalPublishService.publish(id);
    }

    @Operation(summary = "축제 발행 취소",
            description = "발행되지 않은 축제에 요청해도 오류가 아니다.")
    @ApiResponse(responseCode = "200", description = "발행 상태")
    @ApiResponse(responseCode = "404", description = "FESTIVAL_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}/publish")
    public FestivalPublishResponse unpublish(@PathVariable Long id) {
        return festivalPublishService.unpublish(id);
    }
}
