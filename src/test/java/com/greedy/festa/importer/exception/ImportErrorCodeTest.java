package com.greedy.festa.importer.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImportErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
            "IMPORT_NOT_FOUND,404",
            "IMPORT_ALREADY_COMMITTED,409",
            "IMPORT_EXPIRED,410",
            "IMPORT_UNSUPPORTED_PREVIEW_VERSION,409",
            "IMPORT_INVALID_PREVIEW,500",
            "IMPORT_INVALID_LINE_SELECTION,400",
            "IMPORT_UNCOMMITTABLE,409",
            "IMPORT_PREVIEW_STALE,409"
    })
    void commit_오류의_HTTP_status가_계약과_일치한다(
            ImportErrorCode errorCode, int status
    ) {
        assertThat(errorCode.getStatus().value()).isEqualTo(status);
    }
}
