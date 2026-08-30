package com.greedy.festa.importer.model;

import com.greedy.festa.global.util.EnumParser;
import com.greedy.festa.importer.exception.ImportErrorCode;

public enum ImportBatchStatus {
    PENDING,
    COMMITTED,
    EXPIRED;

    public static ImportBatchStatus from(String value) {
        return EnumParser.parse(
                ImportBatchStatus.class, value,
                ImportErrorCode.IMPORT_INVALID_STATUS
        );
    }
}
