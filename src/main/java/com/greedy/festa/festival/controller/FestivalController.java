package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalCardResponse;
import com.greedy.festa.festival.service.FestivalService;
import com.greedy.festa.global.dto.ItemsResponse;
import com.greedy.festa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "축제", description = "축제 조회. 발행된 축제만 내려간다.")
@RestController
@RequestMapping("/api/festivals")
@RequiredArgsConstructor
public class FestivalController {

    private final FestivalService festivalService;

    @Operation(summary = "다가오는 축제 조회",
            description = "종료되지 않은 축제를 시작일 오름차순으로 준다 — 진행 중인 축제가 앞에 온다. "
                    + "종료 판정은 한국 시간의 오늘을 기준으로 한다. limit은 1~50.")
    @ApiResponse(responseCode = "200", description = "다가오는 축제 목록")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_INVALID_LIMIT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/upcoming")
    public ItemsResponse<FestivalCardResponse> getUpcomingFestivals(
            @RequestParam(defaultValue = "10") int limit) {
        return ItemsResponse.of(festivalService.getUpcomingFestivals(limit));
    }

    @Operation(summary = "최근 등록된 축제 조회",
            description = "발행 시각 역순. 진행 상태로 거르지 않으므로 이미 종료된 축제도 포함된다. limit은 1~30.")
    @ApiResponse(responseCode = "200", description = "최근 등록된 축제 목록")
    @ApiResponse(responseCode = "400", description = "FESTIVAL_INVALID_LIMIT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/recent")
    public ItemsResponse<FestivalCardResponse> getRecentFestivals(
            @RequestParam(defaultValue = "10") int limit) {
        return ItemsResponse.of(festivalService.getRecentPublished(limit));
    }
}
