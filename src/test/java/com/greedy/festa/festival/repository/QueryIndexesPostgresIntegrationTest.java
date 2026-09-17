package com.greedy.festa.festival.repository;

import com.greedy.festa.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QueryIndexesPostgresIntegrationTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void coverage와_artist_lineup_조회_인덱스가_정의대로_Flyway로_생성된다() {
        assertThat(indexDefinition("festival", "idx_festival_host_start_id_unpublished"))
                .contains("(HOST_ID, START_DATE, ID)")
                .contains("WHERE (PUBLISHED_AT IS NULL)");
        assertThat(indexDefinition("festival", "idx_festival_host_start_id_published"))
                .contains("(HOST_ID, START_DATE, ID) INCLUDE (END_DATE)")
                .contains("WHERE (PUBLISHED_AT IS NOT NULL)");
        assertThat(indexDefinition("lineup", "idx_lineup_artist_id"))
                .contains("ON " + currentSchema().toUpperCase(Locale.ROOT) + ".LINEUP USING BTREE (ARTIST_ID)");
        assertThat(indexDefinition("artist_alias", "idx_artist_alias_artist_id"))
                .contains("ON " + currentSchema().toUpperCase(Locale.ROOT) + ".ARTIST_ALIAS USING BTREE (ARTIST_ID)");
        assertThat(indexDefinition("festival", "idx_festival_host_id"))
                .contains("ON " + currentSchema().toUpperCase(Locale.ROOT) + ".FESTIVAL USING BTREE (HOST_ID)");
        assertThat(indexDefinition("festival", "idx_festival_published_at_id_desc"))
                .contains("(PUBLISHED_AT DESC, ID DESC)")
                .contains("WHERE (PUBLISHED_AT IS NOT NULL)");
    }

    @Test
    @Transactional
    void coverage와_artist_lineup_실제_쿼리가_각_인덱스를_사용한다() {
        Long hostId = 1L;
        LocalDate yearStart = LocalDate.now().withDayOfYear(1);
        LocalDate nextYearStart = yearStart.plusYears(1);

        assertUsesIndex(explain("""
                SELECT f.id, f.name, f.start_date, f.end_date
                FROM festival f
                WHERE f.host_id = ?
                  AND f.start_date >= ?
                  AND f.start_date < ?
                  AND f.published_at IS NULL
                ORDER BY f.start_date ASC, f.id ASC
                LIMIT 1
                """, hostId, yearStart, nextYearStart), "idx_festival_host_start_id_unpublished");
        assertUsesIndex(explain("""
                SELECT f.id
                FROM festival f
                WHERE f.host_id = ?
                  AND f.start_date >= ?
                  AND f.start_date < ?
                  AND f.end_date >= ?
                  AND f.published_at IS NOT NULL
                ORDER BY f.start_date ASC, f.id ASC
                LIMIT 1
                """, hostId, yearStart, nextYearStart, LocalDate.now()), "idx_festival_host_start_id_published");
        assertUsesIndex(explain("SELECT count(*) FROM lineup WHERE artist_id = ?", 1L),
                "idx_lineup_artist_id");
        assertUsesIndex(explain("SELECT id FROM artist_alias WHERE artist_id = ?", 1L),
                "idx_artist_alias_artist_id");
        assertUsesIndex(explain("SELECT count(*) FROM festival WHERE host_id = ?", hostId),
                "idx_festival_host_id");
        assertUsesIndex(explain("""
                SELECT id FROM festival
                WHERE published_at IS NOT NULL
                ORDER BY published_at DESC, id DESC
                LIMIT 10
                """), "idx_festival_published_at_id_desc");
    }

    private String indexDefinition(String tableName, String indexName) {
        List<String> definitions = jdbcTemplate.query("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = ?
                  AND indexname = ?
                """, (resultSet, rowNum) -> resultSet.getString(1), tableName, indexName);

        assertThat(definitions).as("index %s exists on %s", indexName, tableName).hasSize(1);
        return normalize(definitions.getFirst());
    }

    private String explain(String sql, Object... arguments) {
        jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
        return String.join("\n", jdbcTemplate.query("EXPLAIN " + sql,
                (resultSet, rowNum) -> resultSet.getString(1), arguments));
    }

    private void assertUsesIndex(String plan, String indexName) {
        assertThat(normalize(plan)).contains(indexName.toUpperCase(Locale.ROOT));
    }

    private String currentSchema() {
        return jdbcTemplate.queryForObject("SELECT current_schema()", String.class);
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
