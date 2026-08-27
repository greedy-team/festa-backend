package com.greedy.festa.importer.controller;

import com.greedy.festa.global.config.SwaggerConfig;
import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.importer.dto.ImportPreviewResponse;
import com.greedy.festa.importer.dto.ImportCommitRequest;
import com.greedy.festa.importer.dto.ImportCommitResponse;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.service.ImportPreviewService;
import com.greedy.festa.importer.service.ImportCommitService;
import com.greedy.festa.importer.service.ImportHistoryService;
import com.greedy.festa.importer.model.ImportBatchStatus;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.importer.dto.ImportHistoryItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Tag(name = "관리자 - 임포트", description = "CSV를 올려 반영 전 미리보기 배치를 만든다. 이 단계에서 데이터는 바뀌지 않는다. "
        + "토큰이 없거나 만료되면 401(UNAUTHORIZED / TOKEN_EXPIRED)이다.")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/imports")
@RequiredArgsConstructor
public class ImportAdminController {

    private final ImportPreviewService importPreviewService;
    private final ImportCommitService importCommitService;
    private final ImportHistoryService importHistoryService;
    private final Clock clock;

    @Operation(summary = "임포트 이력 조회",
            description = "type은 BUNDLE / FESTIVALS / LINEUPS / ARTISTS, status는 PENDING / COMMITTED / EXPIRED 중 하나다. 최신 업로드 순으로 내려간다.")
    @ApiResponse(responseCode = "200", description = "임포트 이력 페이지")
    @ApiResponse(responseCode = "400", description = "IMPORT_INVALID_TYPE / IMPORT_INVALID_STATUS / INVALID_PAGE / INVALID_PAGE_SIZE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<ImportHistoryItemResponse> history(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return importHistoryService.findAll(batchType(type), batchStatus(status), page, size);
    }

    @PostMapping("/{importId}/commit")
    public ResponseEntity<ImportCommitResponse> commit(
            @PathVariable("importId") Long importId,
            @RequestBody(required = false) ImportCommitRequest request
    ) {
        return ResponseEntity.ok(importCommitService.commit(importId, request));
    }

    @Operation(summary = "임포트 묶음 미리보기",
            description = "festivals와 lineups는 필수, artists는 선택이다. onConflict는 UPDATE(기본) / SKIP.")
    @ApiResponse(responseCode = "201", description = "생성된 미리보기 배치와 행 목록")
    @ApiResponse(responseCode = "400", description = "IMPORT_MISSING_FILE / IMPORT_INVALID_CONFLICT_POLICY "
            + "/ IMPORT_EMPTY_CSV / IMPORT_INVALID_CSV_ENCODING / IMPORT_INVALID_CSV_HEADER / IMPORT_INVALID_CSV",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(value = "/bundle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> previewBundle(
            @RequestPart(name = "festivals", required = false) MultipartFile festivals,
            @RequestPart(name = "lineups", required = false) MultipartFile lineups,
            @RequestPart(name = "artists", required = false) MultipartFile artists,
            @RequestPart(name = "onConflict", required = false) String onConflict
    ) {
        requireFiles(festivals, lineups);
        return ResponseEntity.status(HttpStatus.CREATED).body(importPreviewService.previewBundle(
                festivals, lineups, artists, conflictPolicy(onConflict), Instant.now(clock)));
    }

    @Operation(summary = "임포트 단건 미리보기",
            description = "type은 festivals / lineups / artists 중 하나다. onConflict는 UPDATE(기본) / SKIP.")
    @ApiResponse(responseCode = "201", description = "생성된 미리보기 배치와 행 목록")
    @ApiResponse(responseCode = "400", description = "IMPORT_INVALID_TYPE / IMPORT_MISSING_FILE "
            + "/ IMPORT_INVALID_CONFLICT_POLICY / IMPORT_EMPTY_CSV / IMPORT_INVALID_CSV_ENCODING / IMPORT_INVALID_CSV_HEADER / IMPORT_INVALID_CSV",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> previewSingle(
            @PathVariable("type") String type,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestPart(name = "onConflict", required = false) String onConflict
    ) {
        requireFiles(file);
        ImportSection section = ImportSection.fromPath(type);
        if (section == null) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_TYPE);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(importPreviewService.previewSingle(
                section, file, conflictPolicy(onConflict), Instant.now(clock)));
    }

    private ImportConflictPolicy conflictPolicy(String value) {
        if (value == null || value.isBlank()) {
            return ImportConflictPolicy.UPDATE;
        }
        try {
            return ImportConflictPolicy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_CONFLICT_POLICY);
        }
    }

    private ImportBatchType batchType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ImportBatchType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_TYPE);
        }
    }

    private ImportBatchStatus batchStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ImportBatchStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_STATUS);
        }
    }

    private void requireFiles(MultipartFile... files) {
        for (MultipartFile file : files) {
            if (file == null) {
                throw new FestaException(ImportErrorCode.IMPORT_MISSING_FILE);
            }
        }
    }
}
