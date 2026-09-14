package com.greedy.festa.importer.model;

import com.greedy.festa.importer.entity.ImportConflictPolicy;

import java.util.List;

public record StoredImportPreview(
        int schemaVersion,
        ImportConflictPolicy conflictPolicy,
        List<StoredPreviewRow> rows
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
