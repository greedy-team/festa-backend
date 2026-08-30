package com.greedy.festa.importer.entity;

import com.greedy.festa.global.util.EnumParser;
import com.greedy.festa.importer.exception.ImportErrorCode;

public enum ImportConflictPolicy {
    UPDATE,
    SKIP;

    public static ImportConflictPolicy from(String value) {
        return EnumParser.parse(
                ImportConflictPolicy.class, value,
                UPDATE, ImportErrorCode.IMPORT_INVALID_CONFLICT_POLICY
        );
    }
}
