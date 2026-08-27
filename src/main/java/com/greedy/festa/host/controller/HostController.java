package com.greedy.festa.host.controller;

import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.host.dto.HostDetailResponse;
import com.greedy.festa.host.service.HostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주최", description = "서비스 사용자에게 공개되는 주최 조회. 인증을 요구하지 않는다.")
@RestController
@RequestMapping("/api/hosts")
@RequiredArgsConstructor
public class HostController {

    private final HostService hostService;

    @Operation(summary = "주최 상세 조회",
            description = "발행된 축제만 집계한다. 다가오는 축제와 이력은 종료일로 갈리며 기준일은 KST다.")
    @ApiResponse(responseCode = "200", description = "주최 상세")
    @ApiResponse(responseCode = "400", description = "INVALID_PATH_VARIABLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "HOST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public HostDetailResponse getHostDetail(@PathVariable Long id) {
        return hostService.getHostDetail(id);
    }
}
