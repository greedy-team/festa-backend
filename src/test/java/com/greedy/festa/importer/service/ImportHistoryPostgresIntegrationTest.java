package com.greedy.festa.importer.service;

import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.model.ImportBatchStatus;
import com.greedy.festa.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
@SpringBootTest
@ActiveProfiles("test")
@Import(ImportHistoryPostgresIntegrationTest.FixedClockConfig.class)
@Transactional
class ImportHistoryPostgresIntegrationTest extends PostgresTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-19T16:10:00Z");

    @Autowired ImportHistoryService service;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 파생_상태_filter_pagination_count_ARRAY와_정렬을_PostgreSQL에서_검증한다() {
        Long adminId = insertAdmin("haeun");
        Instant sameUploadedAt = NOW.minusSeconds(60);
        Long olderPending = insertBatch("BUNDLE", "{older.csv}", sameUploadedAt,
                NOW.plusSeconds(60), null, adminId);
        Long newerPending = insertBatch("BUNDLE", "{newer.csv,NULL}", sameUploadedAt,
                NOW.plusSeconds(120), null, adminId);
        Long expiredAtNow = insertBatch("FESTIVALS", "{expired.csv}", NOW.minusSeconds(120),
                NOW, null, null);
        Long committedAfterExpiry = insertBatch("ARTISTS", "{committed.csv}", NOW.minusSeconds(180),
                NOW.minusSeconds(1), NOW.minusSeconds(10), null);

        var pendingFirstPage = service.findAll(null, ImportBatchStatus.PENDING, 0, 1);
        var pendingSecondPage = service.findAll(null, ImportBatchStatus.PENDING, 1, 1);
        var expired = service.findAll(null, ImportBatchStatus.EXPIRED, 0, 20);
        var committed = service.findAll(null, ImportBatchStatus.COMMITTED, 0, 20);
        var bundlePending = service.findAll(
                ImportBatchType.BUNDLE, ImportBatchStatus.PENDING, 0, 20);

        assertThat(pendingFirstPage.totalElements()).isEqualTo(2);
        assertThat(pendingFirstPage.totalPages()).isEqualTo(2);
        assertThat(pendingFirstPage.hasNext()).isTrue();
        assertThat(pendingFirstPage.items().getFirst().importId()).isEqualTo(newerPending);
        assertThat(pendingFirstPage.items().getFirst().fileNames())
                .containsExactly("newer.csv", null);
        assertThat(pendingFirstPage.items().getFirst().uploadedBy()).isEqualTo("haeun");
        assertThat(pendingSecondPage.items().getFirst().importId()).isEqualTo(olderPending);

        assertThat(expired.items()).singleElement()
                .extracting(item -> item.importId(), item -> item.status(), item -> item.result())
                .containsExactly(expiredAtNow, ImportBatchStatus.EXPIRED, null);
        assertThat(committed.items()).singleElement()
                .extracting(item -> item.importId(), item -> item.status())
                .containsExactly(committedAfterExpiry, ImportBatchStatus.COMMITTED);
        assertThat(bundlePending.items()).extracting(item -> item.importId())
                .containsExactly(newerPending, olderPending);
    }

    @Test
    void COMMITTED는_실제_audit의_CREATE_UPDATE_SKIP을_COUNT로_집계한다() {
        Long committed = insertBatch("BUNDLE", "{bundle.csv}", NOW.minusSeconds(60),
                NOW.plusSeconds(60), NOW.minusSeconds(10), null);
        insertAudit(committed, "ARTISTS", "CREATE", 1);
        insertAudit(committed, "ARTISTS", "CREATE", 2);
        insertAudit(committed, "ARTISTS", "UPDATE", 3);
        insertAudit(committed, "FESTIVALS", "SKIP", 4);
        insertAudit(committed, "LINEUPS", "SKIP", 5);
        Long committedWithoutAudit = insertBatch("ARTISTS", "{empty.csv}", NOW.minusSeconds(120),
                NOW.plusSeconds(60), NOW.minusSeconds(20), null);

        var response = service.findAll(null, ImportBatchStatus.COMMITTED, 0, 20);
        var withAudit = response.items().stream()
                .filter(item -> item.importId().equals(committed)).findFirst().orElseThrow();
        var withoutAudit = response.items().stream()
                .filter(item -> item.importId().equals(committedWithoutAudit)).findFirst().orElseThrow();

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(withAudit.result().artists().created()).isEqualTo(2L);
        assertThat(withAudit.result().artists().updated()).isEqualTo(1L);
        assertThat(withAudit.result().artists().skipped()).isZero();
        assertThat(withAudit.result().festivals().skipped()).isEqualTo(1L);
        assertThat(withAudit.result().lineups().skipped()).isEqualTo(1L);
        assertThat(withoutAudit.result()).isNotNull();
        assertThat(withoutAudit.result().artists())
                .extracting("created", "updated", "skipped")
                .containsExactly(0L, 0L, 0L);
    }

    @Test
    void History_index_두_개가_Flyway로_생성된다() {
        assertThat(indexExists("idx_import_commit_row_batch_id")).isTrue();
        assertThat(indexExists("idx_import_batch_uploaded_at_id_desc")).isTrue();
    }

    private Long insertAdmin(String username) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO admin_user (username, password_hash, created_at)
                VALUES (?, 'hash', ?) RETURNING id
                """, Long.class, username, Timestamp.from(NOW));
    }

    private Long insertBatch(String type, String fileNames, Instant uploadedAt,
                             Instant expiresAt, Instant committedAt, Long adminId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO import_batch
                    (type, file_names, on_conflict, preview, uploaded_by_admin_id,
                     uploaded_at, expires_at, committed_at)
                VALUES (?, ?::text[], 'UPDATE', '{}', ?, ?, ?, ?) RETURNING id
                """, Long.class, type, fileNames, adminId, Timestamp.from(uploadedAt),
                Timestamp.from(expiresAt), committedAt == null ? null : Timestamp.from(committedAt));
    }

    private void insertAudit(Long batchId, String section, String action, int line) {
        jdbcTemplate.update("""
                INSERT INTO import_commit_row
                    (batch_id, section, line, import_key, action, payload, committed_at)
                VALUES (?, ?, ?, ?, ?, '{}'::jsonb, ?)
                """, batchId, section, line, "key-" + line, action, Timestamp.from(NOW));
    }

    private boolean indexExists(String indexName) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_indexes
                    WHERE schemaname = current_schema() AND indexname = ?
                )
                """, Boolean.class, indexName);
        return Boolean.TRUE.equals(exists);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock historyTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
