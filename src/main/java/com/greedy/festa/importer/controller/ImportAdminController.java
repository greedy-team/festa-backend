package com.greedy.festa.importer.controller;

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

import java.time.Instant;
import java.util.Locale;

@RestController
@RequestMapping("/admin/imports")
@RequiredArgsConstructor
public class ImportAdminController {

    private final ImportPreviewService importPreviewService;
    private final ImportCommitService importCommitService;
    private final ImportHistoryService importHistoryService;

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

    @PostMapping(value = "/bundle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> previewBundle(
            @RequestPart("festivals") MultipartFile festivals,
            @RequestPart("lineups") MultipartFile lineups,
            @RequestPart(name = "artists", required = false) MultipartFile artists,
            @RequestPart(name = "onConflict", required = false) String onConflict
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importPreviewService.previewBundle(
                festivals, lineups, artists, conflictPolicy(onConflict), Instant.now()));
    }

    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> previewSingle(
            @PathVariable("type") String type,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "onConflict", required = false) String onConflict
    ) {
        ImportSection section = ImportSection.fromPath(type);
        if (section == null) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_TYPE);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(importPreviewService.previewSingle(
                section, file, conflictPolicy(onConflict), Instant.now()));
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
}
