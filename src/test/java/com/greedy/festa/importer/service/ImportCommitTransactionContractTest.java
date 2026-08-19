package com.greedy.festa.importer.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ImportCommitTransactionContractTest {

    @Test
    void commit_public_method는_단일_Transactional_경계다() throws Exception {
        Transactional transactional = ImportCommitService.class
                .getMethod("commit", Long.class,
                        com.greedy.festa.importer.dto.ImportCommitRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }
}
