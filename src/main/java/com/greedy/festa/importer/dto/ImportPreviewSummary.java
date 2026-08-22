package com.greedy.festa.importer.dto;

import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.StoredPreviewRow;

import java.util.List;

public record ImportPreviewSummary(
        int total,
        int toCreate,
        int toUpdate,
        int toSkip,
        int invalid
) {
    public static ImportPreviewSummary from(List<StoredPreviewRow> rows) {
        return new ImportPreviewSummary(
                rows.size(),
                count(rows, ImportPreviewAction.CREATE),
                count(rows, ImportPreviewAction.UPDATE),
                count(rows, ImportPreviewAction.SKIP),
                count(rows, ImportPreviewAction.INVALID));
    }

    private static int count(List<StoredPreviewRow> rows, ImportPreviewAction action) {
        return (int) rows.stream().filter(row -> row.action() == action).count();
    }
}
