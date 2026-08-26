package com.greedy.festa.importer.repository;

import com.greedy.festa.importer.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;

import com.greedy.festa.importer.entity.ImportBatchType;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT batch FROM ImportBatch batch WHERE batch.id = :id")
    Optional<ImportBatch> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "uploadedByAdmin")
    @Query("""
            SELECT batch FROM ImportBatch batch
            WHERE (:type IS NULL OR batch.type = :type)
              AND (
                :status IS NULL
                OR (:status = 'COMMITTED'
                    AND batch.committedAt IS NOT NULL)
                OR (:status = 'EXPIRED'
                    AND batch.committedAt IS NULL AND batch.expiresAt <= :now)
                OR (:status = 'PENDING'
                    AND batch.committedAt IS NULL AND batch.expiresAt > :now)
              )
            """)
    Page<ImportBatch> findHistory(
            @Param("type") ImportBatchType type,
            @Param("status") String status,
            @Param("now") Instant now,
            Pageable pageable);
}
