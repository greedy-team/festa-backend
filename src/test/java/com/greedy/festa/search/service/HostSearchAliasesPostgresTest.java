package com.greedy.festa.search.service;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.search.dto.SearchResponse;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.FestivalFixture;
import com.greedy.festa.support.fixture.HostFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaConfig.class, SearchService.class,
        SearchServicePostgresIntegrationTest.FixedClockConfig.class})
class HostSearchAliasesPostgresTest extends PostgresTestSupport {

    @Autowired EntityManager entityManager;
    @Autowired SearchService searchService;

    static Stream<Arguments> aliases() {
        return HostSearchAliases.mappings().entrySet().stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest
    @MethodSource("aliases")
    void approvedAliasFindsItsHostAndPublishedFestival(String alias, String officialName) {
        Host host = HostFixture.host(officialName).build();
        entityManager.persist(host);
        Festival festival = FestivalFixture.festival(officialName + " 축제")
                .host(host)
                .build();
        festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        entityManager.persist(festival);
        entityManager.flush();
        entityManager.clear();

        SearchResponse all = searchService.search(alias, "ALL");

        assertThat(all.hosts()).extracting(item -> item.hostId()).containsExactly(host.getId());
        assertThat(all.festivals()).extracting(item -> item.festivalId()).containsExactly(festival.getId());
        assertThat(all.counts().host()).isOne();
        assertThat(all.counts().festival()).isOne();
        assertThat(searchService.search(alias, "HOST").counts()).isEqualTo(all.counts());
        assertThat(searchService.search(alias, "FESTIVAL").counts()).isEqualTo(all.counts());
    }

    @ParameterizedTest
    @MethodSource("aliases")
    void innerAsciiSpacesStillResolveApprovedAliases(String alias, String officialName) {
        Host host = HostFixture.host(officialName).build();
        entityManager.persist(host);
        Festival festival = FestivalFixture.festival("축제")
                .host(host)
                .build();
        festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        entityManager.persist(festival);
        entityManager.flush();
        entityManager.clear();

        String spacedAlias = alias.substring(0, 1) + " " + alias.substring(1);
        SearchResponse result = searchService.search(spacedAlias, "ALL");

        assertThat(result.hosts()).extracting(item -> item.hostId()).containsExactly(host.getId());
        assertThat(result.festivals()).extracting(item -> item.festivalId()).containsExactly(festival.getId());
    }

    @org.junit.jupiter.api.Test
    void existingShortNameResultIsNotDuplicatedByAliasExpansion() {
        Host host = HostFixture.host("한국외국어대학교").shortName("한국외대").build();
        entityManager.persist(host);
        Festival festival = FestivalFixture.festival("축제")
                .host(host)
                .build();
        festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        entityManager.persist(festival);
        entityManager.flush();
        entityManager.clear();

        SearchResponse result = searchService.search("한국외대", "ALL");

        assertThat(result.hosts()).extracting(item -> item.hostId()).containsExactly(host.getId());
        assertThat(result.festivals()).extracting(item -> item.festivalId()).containsExactly(festival.getId());
        assertThat(result.counts().host()).isOne();
        assertThat(result.counts().festival()).isOne();
    }

    @org.junit.jupiter.api.Test
    void aliasExpansionKeepsHostOrderAfterResultsAreMerged() {
        entityManager.createNativeQuery("ALTER TABLE host ALTER COLUMN id RESTART WITH 5")
                .executeUpdate();
        Host officialNameMatch = HostFixture.host("한국외국어대학교").build();
        entityManager.persist(officialNameMatch);
        entityManager.flush();
        entityManager.createNativeQuery("ALTER TABLE host ALTER COLUMN id RESTART WITH 90")
                .executeUpdate();
        Host directMatch = HostFixture.host("한국외국어대학교 서울캠퍼스")
                .shortName("외대")
                .build();
        entityManager.persist(directMatch);
        entityManager.flush();
        entityManager.clear();

        SearchResponse result = searchService.search("외대", "HOST");

        assertThat(result.hosts()).extracting(item -> item.hostId())
                .containsExactly(officialNameMatch.getId(), directMatch.getId());
        assertThat(result.counts().host()).isEqualTo(2);
    }

    @org.junit.jupiter.api.Test
    void aliasExpansionKeepsFestivalOrderAfterResultsAreMerged() {
        Host host = HostFixture.host("건국대학교").shortName("건대").build();
        entityManager.persist(host);
        Festival directMatch = FestivalFixture.festival("직접 일치 축제")
                .host(host)
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 3))
                .build();
        Festival officialNameMatch = FestivalFixture.festival("정식명 일치 축제")
                .host(host)
                .startDate(LocalDate.of(2026, 9, 20))
                .endDate(LocalDate.of(2026, 9, 22))
                .build();
        directMatch.publish(Instant.parse("2026-08-01T00:00:00Z"));
        officialNameMatch.publish(Instant.parse("2026-08-01T00:00:00Z"));
        entityManager.persist(directMatch);
        entityManager.persist(officialNameMatch);
        entityManager.flush();
        entityManager.clear();

        SearchResponse result = searchService.search("건대", "FESTIVAL");

        assertThat(result.festivals()).extracting(item -> item.festivalId())
                .containsExactly(officialNameMatch.getId(), directMatch.getId());
        assertThat(result.counts().festival()).isEqualTo(2);
    }

    @org.junit.jupiter.api.Test
    void unlistedAbbreviationIsNotInferred() {
        Host host = HostFixture.host("건국대학교").build();
        entityManager.persist(host);
        Festival festival = FestivalFixture.festival("축제")
                .host(host)
                .build();
        festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        entityManager.persist(festival);
        entityManager.flush();
        entityManager.clear();

        SearchResponse result = searchService.search("건구대", "ALL");

        assertThat(result.hosts()).isEmpty();
        assertThat(result.festivals()).isEmpty();
    }
}
