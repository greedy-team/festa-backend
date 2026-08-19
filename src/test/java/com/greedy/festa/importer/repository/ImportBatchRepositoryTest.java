package com.greedy.festa.importer.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

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
}
