package com.greedy.festa.importer.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class ImportCommitRowRepositoryTest {

    @Test
    void history_result는_batch_section_action을_한번에_group_집계한다() throws Exception {
        Query query = ImportCommitRowRepository.class
                .getMethod("aggregateByBatchIds", Collection.class)
                .getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains(
                "commitRow.batch.id IN :batchIds",
                "GROUP BY commitRow.batch.id, commitRow.section, commitRow.action",
                "COUNT(commitRow) AS total");
    }
}
