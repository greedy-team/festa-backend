package com.greedy.festa.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QueryIndexesPostgresIntegrationTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void coverage와_artist_lineup_조회_인덱스가_Flyway로_생성된다() {
        assertThat(indexDefinition("idx_festival_host_start_id_unpublished"))
                .contains("(HOST_ID, START_DATE, ID)")
                .contains("WHERE (PUBLISHED_AT IS NULL)");
        assertThat(indexDefinition("idx_festival_host_start_id_published"))
                .contains("(HOST_ID, START_DATE, ID) INCLUDE (END_DATE)")
                .contains("WHERE (PUBLISHED_AT IS NOT NULL)");
        assertThat(indexDefinition("idx_lineup_artist_id"))
                .contains("ON PUBLIC.LINEUP USING BTREE (ARTIST_ID)");
    }

    private String indexDefinition(String indexName) {
        String definition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema() AND indexname = ?
                """, String.class, indexName);

        assertThat(definition).as("index %s exists", indexName).isNotNull();
        return definition.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
