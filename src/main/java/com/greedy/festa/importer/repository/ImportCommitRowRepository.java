package com.greedy.festa.importer.repository;

import com.greedy.festa.importer.entity.ImportCommitRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ImportCommitRowRepository extends JpaRepository<ImportCommitRow, Long> {

    @Query("""
            SELECT commitRow.batch.id AS batchId, commitRow.section AS section,
                   commitRow.action AS action, COUNT(commitRow) AS total
            FROM ImportCommitRow commitRow
            WHERE commitRow.batch.id IN :batchIds
            GROUP BY commitRow.batch.id, commitRow.section, commitRow.action
            """)
    List<ImportCommitAggregateRow> aggregateByBatchIds(
            @Param("batchIds") Collection<Long> batchIds);
}
