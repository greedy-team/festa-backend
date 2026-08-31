package com.greedy.festa.artist.controller;

import com.greedy.festa.artist.dto.LineupAdminDetailResponse;
import com.greedy.festa.artist.dto.LineupUpdateRequest;
import com.greedy.festa.artist.service.LineupAdminService;
import com.greedy.festa.global.config.SwaggerConfig;
import com.greedy.festa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 라인업", description = "관리자 라인업 조회")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/lineups")
@RequiredArgsConstructor
public class LineupAdminController {

    private final LineupAdminService lineupAdminService;

    @Operation(summary = "라인업 단건 조회")
    @ApiResponse(responseCode = "200", description = "라인업 상세")
    @ApiResponse(responseCode = "404", description = "LINEUP_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public LineupAdminDetailResponse findOne(@PathVariable Long id) {
        return lineupAdminService.findOne(id);
    }

    @Operation(summary = "라인업 수정", description = "day와 displayOrder는 필수이며 artistId가 비면 시크릿 게스트로 저장합니다.")
    @PatchMapping("/{id}")
    public LineupAdminDetailResponse update(@PathVariable Long id, @RequestBody LineupUpdateRequest request) {
        return lineupAdminService.update(id, request);
    }
}
