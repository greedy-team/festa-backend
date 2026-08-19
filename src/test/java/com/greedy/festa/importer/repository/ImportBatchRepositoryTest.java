package com.greedy.festa.importer.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class ImportBatchRepositoryTest {

    @Test
    void commit_조회는_PESSIMISTIC_WRITE_lock_계약이다() throws Exception {
        Lock lock = ImportBatchRepository.class
                .getMethod("findByIdForUpdate", Long.class)
                .getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void history_조회는_uploader를_함께_조회하고_status를_DB에서_filter한다() throws Exception {
        var method = ImportBatchRepository.class.getMethod(
                "findHistory", com.greedy.festa.importer.entity.ImportBatchType.class,
                String.class, java.time.Instant.class, org.springframework.data.domain.Pageable.class);
        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths()).containsExactly("uploadedByAdmin");
        assertThat(query.value()).contains(
                "batch.committedAt IS NOT NULL",
                "batch.committedAt IS NULL AND batch.expiresAt <= :now",
                "batch.committedAt IS NULL AND batch.expiresAt > :now");
        assertThat(query.countQuery()).contains("COUNT(batch)", "batch.expiresAt <= :now");
        String contentWhere = query.value().substring(query.value().indexOf("WHERE"))
                .replaceAll("\\s+", " ").trim();
        String countWhere = query.countQuery().substring(query.countQuery().indexOf("WHERE"))
                .replaceAll("\\s+", " ").trim();
        assertThat(countWhere).isEqualTo(contentWhere);
    }
}
