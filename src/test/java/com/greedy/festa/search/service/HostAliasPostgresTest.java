package com.greedy.festa.search.service;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.entity.HostAlias;
import com.greedy.festa.host.service.HostAdminService;
import com.greedy.festa.search.dto.SearchResponse;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.FestivalFixture;
import com.greedy.festa.support.fixture.HostFixture;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.schemas=host_alias_search",
        "spring.flyway.default-schema=host_alias_search",
        "spring.jpa.properties.hibernate.default_schema=host_alias_search"
})
@Import({JpaConfig.class, SearchService.class, HostAdminService.class,
        SearchServicePostgresIntegrationTest.FixedClockConfig.class})
class HostAliasPostgresTest extends PostgresTestSupport {
    @Autowired private EntityManager em;
    @Autowired private SearchService search;
    @Autowired private HostAdminService admin;
    @Autowired private FestivalRepository festivals;
    @Autowired private DataSource dataSource;

    private static final List<String> EXPECTED = List.of(
            "연세대학교:연대", "고려대학교:고대", "홍익대학교:홍대",
            "한국외국어대학교:외대", "한국외국어대학교:한국외대",
            "이화여자대학교:이대", "이화여자대학교:이화여대",
            "숙명여자대학교:숙대", "숙명여자대학교:숙명여대",
            "서울여자대학교:서울여대", "서울여자대학교:설여대",
            "동덕여자대학교:동덕여대", "동덕여자대학교:동덕대",
            "덕성여자대학교:덕성여대", "덕성여자대학교:덕성대", "건국대학교:건대");

    @Test
    void seededAliasesMatchExactHostsAndPublishedFestivals() {
        assertThat(em.createQuery("select concat(a.host.name, ':', a.name) from HostAlias a", String.class)
                .getResultList()).containsExactlyInAnyOrderElementsOf(EXPECTED);
        for (String pair : EXPECTED) {
            String[] parts = pair.split(":");
            Host host = em.createQuery("from Host where name = :name", Host.class)
                    .setParameter("name", parts[0]).getSingleResult();
            Festival festival = festival(host, "정기 행사", true);
            em.flush();
            SearchResponse result = search.search(parts[1], "ALL");
            assertThat(result.hosts()).extracting(h -> h.hostId()).contains(host.getId());
            assertThat(result.festivals()).extracting(f -> f.festivalId()).contains(festival.getId());
            assertCounts(parts[1], result);
        }
    }

    @Test
    void multipleAliasesDoNotMultiplyResultsAndListsRemainNameOnly() {
        Host host = host("Unique School", "Unique Short");
        alias(host, "Unique Alias");
        alias(host, "Unique Alias Second");
        Festival visible = festival(host, "Unrelated event", true);
        festival(host, "Hidden event", false);
        em.flush();
        em.clear();
        SearchResponse result = search.search("  unique alias  ", "ALL");
        assertThat(result.query()).isEqualTo("unique alias");
        assertThat(result.hosts()).extracting(h -> h.hostId()).containsExactly(host.getId());
        assertThat(result.hosts().getFirst().festivalCount()).isEqualTo(1);
        assertThat(result.festivals()).extracting(f -> f.festivalId()).containsExactly(visible.getId());
        assertCounts("unique alias", result);
        for (String oldQuery : List.of("Unique School", "Unique Short")) {
            SearchResponse oldResult = search.search(oldQuery, "ALL");
            assertThat(oldResult.hosts()).isEqualTo(result.hosts());
            assertThat(oldResult.festivals()).isEqualTo(result.festivals());
            assertCounts(oldQuery, oldResult);
        }
        assertThat(festivals.findPublishedRows(null, null, null, null, null,
                LocalDate.of(2026, 9, 9), "Unique Alias", PageRequest.of(0, 1)).getTotalElements()).isZero();
        assertThat(festivals.findReviewRows(null, null, null, null, "Unique Alias", null,
                PageRequest.of(0, 1)).getTotalElements()).isZero();
    }

    @Test
    void aliasLikeMetacharactersRemainLiteral() {
        Host literal = host("Literal school", null);
        alias(literal, "Needle%_\\Tail");
        Host decoy = host("Decoy school", null);
        alias(decoy, "NeedleXXTail");
        festival(literal, "Literal event", true);
        festival(decoy, "Decoy event", true);
        em.flush();
        for (String query : List.of("%", "_", "\\", "Needle%_\\Tail")) {
            SearchResponse result = search.search(query, "ALL");
            assertThat(result.hosts()).extracting(h -> h.hostId()).containsExactly(literal.getId());
            assertThat(result.festivals()).hasSize(1);
            assertCounts(query, result);
        }
    }

    @Test
    void sharedAliasesKeepDistinctHostsAndFestivalOrderWithoutWhitespaceNormalization() {
        Host first = host("First academy", null);
        Host second = host("Second academy", "Second display");
        Host empty = host("Empty academy", null);
        alias(first, "Shared Needle");
        alias(first, "Shared Needle extra");
        alias(second, "Shared Needle");
        alias(empty, "Shared Needle");
        Festival earlier = festival(second, "Earlier event", true);
        Festival later = festival(first, "Later event", true);
        festival(first, "Private event", false);
        em.flush();
        em.clear();

        SearchResponse result = search.search("shared needle", "ALL");
        assertThat(result.hosts()).extracting(h -> h.hostId())
                .containsExactly(first.getId(), second.getId(), empty.getId());
        assertThat(result.hosts()).extracting(h -> h.festivalCount()).containsExactly(1L, 1L, 0L);
        assertThat(result.festivals()).extracting(f -> f.festivalId())
                .containsExactly(earlier.getId(), later.getId());
        assertThat(result.counts().host()).isEqualTo(3);
        assertThat(result.counts().festival()).isEqualTo(2);
        assertCounts("shared needle", result);

        SearchResponse noSpace = search.search("sharedneedle", "ALL");
        assertThat(noSpace.hosts()).isEmpty();
        assertThat(noSpace.festivals()).isEmpty();
        assertCounts("sharedneedle", noSpace);
    }

    @Test
    void hostDeletionCascadesAndSameAliasOnDifferentHostsIsAllowed() {
        Host first = host("First school", null);
        Host second = host("Second school", null);
        alias(first, "Shared alias");
        alias(second, "Shared alias");
        em.flush();
        // The delete API starts a new persistence context; aliases are managed by the DB cascade.
        em.clear();
        admin.delete(first.getId());
        em.flush();
        em.clear();
        assertThat(search.search("Shared alias", "ALL").hosts())
                .extracting(h -> h.hostId()).containsExactly(second.getId());
        assertThat(em.createQuery("select count(a) from HostAlias a where a.host.id = :id", Long.class)
                .setParameter("id", first.getId()).getSingleResult()).isZero();
    }

    @Test
    void duplicateAliasWithinHostIsRejected() {
        Host host = host("Duplicate school", null);
        alias(host, "Same alias");
        assertThatThrownBy(() -> { alias(host, "Same alias"); em.flush(); })
                .hasStackTraceContaining("uq_host_alias_host_name");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void flywayUpgradePreservesHostDataAndFailsAtomicallyForMissingHost() {
        String schema = "alias_test_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("20260909.2000").load().migrate();
            // Only exact names may link aliases, regardless of environment-specific IDs.
            jdbc.execute("ALTER TABLE " + schema + ".host ALTER COLUMN id RESTART WITH 10000");
            jdbc.update("UPDATE " + schema + ".host SET id = DEFAULT");
            jdbc.update("UPDATE " + schema + ".host SET short_name = 'custom display' WHERE name = '연세대학교'");
            jdbc.update("UPDATE " + schema + ".host SET name = 'renamed host' WHERE name = '건국대학교'");
            Flyway upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            assertThatThrownBy(upgrade::migrate).hasStackTraceContaining("requires all 10 named hosts");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".host_alias", Long.class)).isZero();
            jdbc.update("UPDATE " + schema + ".host SET name = '건국대학교' WHERE name = 'renamed host'");
            upgrade.migrate();
            assertThat(jdbc.queryForList("SELECT h.name || ':' || a.name FROM " + schema
                    + ".host_alias a JOIN " + schema + ".host h ON h.id = a.host_id", String.class))
                    .containsExactlyInAnyOrderElementsOf(EXPECTED);
            assertThat(jdbc.queryForObject("SELECT short_name FROM " + schema
                    + ".host WHERE name = '연세대학교'", String.class)).isEqualTo("custom display");
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema
                    + ".host_alias (host_id, name) VALUES (-1, 'orphan')"))
                    .hasStackTraceContaining("fk_host_alias_host");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema
                    + ".host_alias (host_id, name) SELECT id, NULL FROM " + schema + ".host LIMIT 1"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void assertCounts(String query, SearchResponse all) {
        assertThat(all.counts().host()).isEqualTo(all.hosts().size());
        assertThat(all.counts().festival()).isEqualTo(all.festivals().size());
        assertThat(all.counts().artist()).isEqualTo(all.artists().size());
        for (String type : List.of("HOST", "FESTIVAL", "ARTIST")) {
            SearchResponse selected = search.search(query, type);
            assertThat(selected.counts()).isEqualTo(all.counts());
            assertThat(selected.hosts()).isEqualTo(type.equals("HOST") ? all.hosts() : List.of());
            assertThat(selected.festivals()).isEqualTo(type.equals("FESTIVAL") ? all.festivals() : List.of());
        }
    }

    private Host host(String name, String shortName) {
        Host host = HostFixture.host(name).shortName(shortName).build();
        em.persist(host);
        return host;
    }

    private void alias(Host host, String name) {
        em.persist(HostAlias.builder().host(host).name(name).build());
    }

    private Festival festival(Host host, String name, boolean published) {
        Festival festival = FestivalFixture.festival(name).host(host).build();
        if (published) festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        em.persist(festival);
        return festival;
    }
}
