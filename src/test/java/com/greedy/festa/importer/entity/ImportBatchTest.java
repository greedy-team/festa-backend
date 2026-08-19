package com.greedy.festa.importer.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ImportBatchTest {

    @Test
    void uploadedAt은_null일_수_없다() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImportBatch.builder()
                        .type(ImportBatchType.FESTIVALS)
                        .fileNames(List.of("festivals.csv"))
                        .onConflict(ImportConflictPolicy.UPDATE)
                        .uploadedAt(null)
                        .build())
                .withMessage("uploadedAt");
    }
}
