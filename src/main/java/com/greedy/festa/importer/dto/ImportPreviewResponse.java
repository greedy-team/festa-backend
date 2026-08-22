package com.greedy.festa.importer.dto;

import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.model.PreviewProblem;
import com.greedy.festa.importer.model.StoredPreviewRow;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public record ImportPreviewResponse(
        Long importId,
        ImportBatchType type,
        ImportConflictPolicy onConflict,
        Instant uploadedAt,
        Instant expiresAt,
        ImportPreviewSummary summary,
        List<ImportBlockerResponse> blockers,
        List<ImportPreviewRowResponse> rows
) {
    public static ImportPreviewResponse of(ImportBatch batch, List<StoredPreviewRow> rows) {
        Map<String, BlockerAggregation> blockers = new LinkedHashMap<>();
        rows.forEach(row -> row.errors().stream()
                .filter(PreviewProblem::blocker)
                .forEach(problem -> blockers.computeIfAbsent(
                                problem.code(), ignored -> new BlockerAggregation())
                        .add(blockerValue(row, problem.code()))));
        return new ImportPreviewResponse(
                batch.getId(), batch.getType(), batch.getOnConflict(), batch.getUploadedAt(),
                batch.getExpiresAt(), ImportPreviewSummary.from(rows),
                blockers.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> new ImportBlockerResponse(
                                entry.getKey(), entry.getValue().count,
                                List.copyOf(entry.getValue().values)))
                        .toList(),
                rows.stream().map(ImportPreviewRowResponse::from).toList());
    }

    private static String blockerValue(StoredPreviewRow row, String code) {
        if (code.startsWith("HOST_")) {
            return String.valueOf(row.normalized().getOrDefault("hostName", row.importKey()));
        }
        if (code.startsWith("ARTIST_")) {
            Object value = row.normalized().get("name");
            if (value == null) {
                value = row.normalized().get("artistCanonical");
            }
            if (value == null || value.toString().isBlank()) {
                value = row.normalized().get("artistRaw");
            }
            return value == null || value.toString().isBlank() ? row.importKey() : value.toString();
        }
        return row.importKey();
    }

    private static final class BlockerAggregation {
        private long count;
        private final LinkedHashSet<String> values = new LinkedHashSet<>();

        private void add(String value) {
            count++;
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
    }
}
