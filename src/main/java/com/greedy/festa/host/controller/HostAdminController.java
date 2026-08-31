package com.greedy.festa.host.controller;

import com.greedy.festa.global.config.SwaggerConfig;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.host.dto.HostCreateRequest;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.dto.HostUpdateRequest;
import com.greedy.festa.host.service.HostAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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

@Tag(name = "관리자 - 주최", description = "축제를 여는 주최의 등록·조회·수정·삭제. "
        + "토큰이 없거나 만료되면 401(UNAUTHORIZED / TOKEN_EXPIRED)이다.")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/hosts")
@RequiredArgsConstructor
public class HostAdminController {

    private final HostAdminService hostAdminService;

    @Operation(summary = "주최 등록")
    @ApiResponse(responseCode = "201", description = "등록된 주최")
    @ApiResponse(responseCode = "400", description = "HOST_INVALID_NAME / HOST_INVALID_REGION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "HOST_DUPLICATE_NAME - 이미 등록된 이름",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<HostResponse> create(@RequestBody HostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hostAdminService.create(request));
    }

    @Operation(summary = "주최 목록 조회", description = "등록 역순으로 내려간다. 축제 수는 조회 시점에 세어 채운다.")
    @ApiResponse(responseCode = "200", description = "주최 페이지")
    @ApiResponse(responseCode = "400", description = "INVALID_PAGE / INVALID_PAGE_SIZE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<HostResponse> findAll(Pageable pageable) {
        return hostAdminService.findAll(pageable);
    }

    @Operation(summary = "주최 단건 조회", description = "관리자 수정에 필요한 주최의 현재 값을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "주최 상세")
    @ApiResponse(responseCode = "404", description = "HOST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public HostResponse findOne(@PathVariable Long id) {
        return hostAdminService.findOne(id);
    }

    @Operation(summary = "주최 수정", description = "name과 instagramUrl은 반드시 보내야 한다. "
            + "instagramUrl은 blank면 삭제하고 null은 거절한다. "
            + "logoUrl, bannerUrl, homepageUrl은 생략하면 유지하고 blank면 삭제한다.")
    @ApiResponse(responseCode = "200", description = "수정된 주최")
    @ApiResponse(responseCode = "400", description = "HOST_INVALID_NAME / HOST_INVALID_REGION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "HOST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "HOST_DUPLICATE_NAME",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}")
    public HostResponse update(@PathVariable Long id, @RequestBody HostUpdateRequest request) {
        return hostAdminService.update(id, request);
    }

    @Operation(summary = "주최 삭제", description = "축제가 등록된 주최는 지우지 않는다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @ApiResponse(responseCode = "404", description = "HOST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "HOST_HAS_FESTIVALS - 축제가 등록된 주최",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        hostAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
