package com.greedy.festa.importer.repository;

import com.greedy.festa.importer.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT batch FROM ImportBatch batch WHERE batch.id = :id")
    Optional<ImportBatch> findByIdForUpdate(@Param("id") Long id);
}
