package com.greedy.festa.importer.dto;

import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.model.ImportBatchStatus;

import java.time.Instant;
import java.util.List;

public record ImportHistoryItemResponse(
        Long importId,
        ImportBatchType type,
        List<String> fileNames,
        ImportBatchStatus status,
        String uploadedBy,
        Instant uploadedAt,
        Instant expiresAt,
        Instant committedAt,
        ImportHistoryResult result
) {
}
