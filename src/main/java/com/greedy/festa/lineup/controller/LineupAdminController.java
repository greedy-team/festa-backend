package com.greedy.festa.lineup.controller;

import com.greedy.festa.global.config.SwaggerConfig;
import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.lineup.dto.LineupCreateRequest;
import com.greedy.festa.lineup.dto.LineupResponse;
import com.greedy.festa.lineup.dto.LineupUpdateRequest;
import com.greedy.festa.lineup.service.LineupAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 라인업", description = "축제 라인업의 등록·수정·삭제. "
        + "artistId를 비우면 시크릿 게스트이며 자리는 유지된다. "
        + "토큰이 없거나 만료되면 401(UNAUTHORIZED / TOKEN_EXPIRED)이다.")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/festivals/{festivalId}/lineups")
@RequiredArgsConstructor
public class LineupAdminController {

    private final LineupAdminService lineupAdminService;

    @Operation(summary = "라인업 단건 조회", description = "관리자 수정에 필요한 라인업의 현재 값을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "라인업 상세")
    @ApiResponse(responseCode = "404", description = "LINEUP_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{lineupId}")
    public LineupResponse findOne(@PathVariable Long festivalId, @PathVariable Long lineupId) {
        return lineupAdminService.findOne(festivalId, lineupId);
    }

    @Operation(summary = "라인업 등록",
            description = "day는 1 이상이고 축제 기간을 벗어날 수 없다. "
                    + "artistId를 비우면 시크릿 게스트로 저장된다.")
    @ApiResponse(responseCode = "201", description = "등록된 라인업")
    @ApiResponse(responseCode = "400", description = "LINEUP_INVALID_DAY / LINEUP_INVALID_DISPLAY_ORDER "
            + "/ LINEUP_DAY_OUT_OF_RANGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "FESTIVAL_NOT_FOUND / ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "LINEUP_DUPLICATE_SLOT - 같은 일차의 같은 순서가 이미 있다",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<LineupResponse> create(
            @PathVariable Long festivalId,
            @RequestBody LineupCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lineupAdminService.create(festivalId, request));
    }

    @Operation(summary = "라인업 수정",
            description = "전체 교체다 - 보낸 것이 전부다. artistId를 비우면 시크릿 게스트가 된다.")
    @ApiResponse(responseCode = "200", description = "수정된 라인업")
    @ApiResponse(responseCode = "400", description = "LINEUP_INVALID_DAY / LINEUP_INVALID_DISPLAY_ORDER "
            + "/ LINEUP_DAY_OUT_OF_RANGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "LINEUP_NOT_FOUND / ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "LINEUP_DUPLICATE_SLOT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{lineupId}")
    public LineupResponse update(
            @PathVariable Long festivalId,
            @PathVariable Long lineupId,
            @RequestBody LineupUpdateRequest request) {
        return lineupAdminService.update(festivalId, lineupId, request);
    }

    @Operation(summary = "라인업 삭제",
            description = "그 축제의 라인업이 아니면 404다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @ApiResponse(responseCode = "404", description = "LINEUP_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{lineupId}")
    public ResponseEntity<Void> delete(@PathVariable Long festivalId, @PathVariable Long lineupId) {
        lineupAdminService.delete(festivalId, lineupId);
        return ResponseEntity.noContent().build();
    }
}
