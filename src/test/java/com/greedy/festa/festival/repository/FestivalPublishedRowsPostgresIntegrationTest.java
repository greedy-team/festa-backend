package com.greedy.festa.festival.repository;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.util.LikePatternUtils;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.FestivalFixture;
import com.greedy.festa.support.fixture.HostFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class FestivalPublishedRowsPostgresIntegrationTest extends PostgresTestSupport {

    @Autowired EntityManager entityManager;
    @Autowired FestivalRepository festivalRepository;

    @Test
    void publishedRowsAndCountMatchThePreviousHostInnerJoinQuery() {
        Host firstHost = persistHost("first");
        Festival first = persistPublished("Spring % _ \\ Festival", firstHost, LocalDate.of(2026, 9, 3));
        Festival second = persistPublished("Spring%_\\Festival After", firstHost, LocalDate.of(2026, 9, 2));
        persistPublished("Spring % _ \\ Festival Hidden", null, LocalDate.of(2026, 9, 4));
        persistUnpublished("Spring % _ \\ Festival Draft", firstHost);
        entityManager.flush();
        entityManager.clear();

        String pattern = LikePatternUtils.toSearchPattern(" Spring % _ \\ Festival ");
        PageRequest pageRequest = PageRequest.of(0, 2,
                Sort.by(Sort.Order.desc("startDate"), Sort.Order.asc("id")));

        Page<Festival> actual = festivalRepository.findPublishedRows(
                firstHost.getId(), null, null, null, null, LocalDate.of(2026, 8, 27), pattern, pageRequest);

        assertThat(actual.getContent().stream().map(Festival::getId).toList())
                .containsExactlyElementsOf(previousList(pattern, firstHost.getId(), 2));
        assertThat(actual.getTotalElements()).isEqualTo(previousCount(pattern, firstHost.getId()));
    }

    private Host persistHost(String suffix) {
        Host host = HostFixture.host("host-" + suffix).build();
        entityManager.persist(host);
        return host;
    }

    private Festival persistPublished(String name, Host host, LocalDate startDate) {
        Festival festival = FestivalFixture.festival(name)
                .host(host)
                .startDate(startDate)
                .endDate(startDate.plusDays(1))
                .build();
        festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        entityManager.persist(festival);
        return festival;
    }

    private void persistUnpublished(String name, Host host) {
        entityManager.persist(FestivalFixture.festival(name).host(host).build());
    }

    @SuppressWarnings("unchecked")
    private List<Long> previousList(String pattern, Long hostId, int limit) {
        return entityManager.createNativeQuery("""
                SELECT f.id
                FROM festival f
                JOIN host h ON h.id = f.host_id
                WHERE f.published_at IS NOT NULL
                  AND h.id = :hostId
                  AND LOWER(REPLACE(f.name, ' ', '')) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE E'\\\\'
                ORDER BY f.start_date DESC, f.id ASC
                LIMIT :limit
                """)
                .setParameter("q", pattern)
                .setParameter("hostId", hostId)
                .setParameter("limit", limit)
                .getResultList().stream()
                .map(value -> ((Number) value).longValue())
                .toList();
    }

    private long previousCount(String pattern, Long hostId) {
        return ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM festival f
                JOIN host h ON h.id = f.host_id
                WHERE f.published_at IS NOT NULL
                  AND h.id = :hostId
                  AND LOWER(REPLACE(f.name, ' ', '')) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE E'\\\\'
                """)
                .setParameter("q", pattern)
                .setParameter("hostId", hostId)
                .getSingleResult()).longValue();
    }

}
