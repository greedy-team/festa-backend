package com.greedy.festa.importer.entity;

import com.greedy.festa.global.util.EnumParser;
import com.greedy.festa.importer.exception.ImportErrorCode;

public enum ImportBatchType {
    BUNDLE,
    FESTIVALS,
    LINEUPS,
    ARTISTS;

    public static ImportBatchType from(String value) {
        return EnumParser.parse(
                ImportBatchType.class, value,
                ImportErrorCode.IMPORT_INVALID_TYPE
        );
    }
}
