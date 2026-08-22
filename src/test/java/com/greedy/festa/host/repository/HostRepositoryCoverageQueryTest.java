package com.greedy.festa.host.repository;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class HostRepositoryCoverageQueryTest extends PostgresTestSupport {

    @Autowired
    private HostRepository hostRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 실제_festival_상태로_coverage_projection을_계산한다() {
        Host 없음 = host("No Festival");

        Host 미발행만 = host("Unpublished Only");
        festival(미발행만, "Unpublished", LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3), false);

        Host 발행현재 = host("Published Current");
        festival(발행현재, "Published Current Festival", LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3), true);

        Host 발행종료 = host("Published Closed");
        festival(발행종료, "Published Closed Festival", LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3), true);

        Host 날짜상현재지만미발행 = host("Unpublished Current Date");
        festival(날짜상현재지만미발행, "Unpublished Current Festival",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), false);

        Host 발행과미발행 = host("Published And Unpublished");
        festival(발행과미발행, "Published Festival", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 3), true);
        festival(발행과미발행, "Unpublished Festival", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3), false);

        em.flush();
        em.clear();

        Map<String, HostCoverageRow> rows = hostRepository.findCoverageRows(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2027, 1, 1),
                        LocalDate.of(2026, 5, 1))
                .stream()
                .collect(Collectors.toMap(HostCoverageRow::getHostName, Function.identity()));

        assertProjection(rows.get(없음.getName()), false, false);
        assertProjection(rows.get(미발행만.getName()), true, false);
        assertProjection(rows.get(발행현재.getName()), false, true);
        assertProjection(rows.get(발행종료.getName()), false, false);
        assertProjection(rows.get(날짜상현재지만미발행.getName()), true, false);
        assertProjection(rows.get(발행과미발행.getName()), true, true);
        assertThat(rows.get(발행과미발행.getName()).getFestivalName())
                .isEqualTo("Unpublished Festival");
    }

    private Host host(String name) {
        Host host = Host.builder().name(name).region("Seoul").build();
        em.persist(host);
        return host;
    }

    private void festival(
            Host host,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean published
    ) {
        Festival festival = Festival.builder()
                .host(host)
                .name(name)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        em.persist(festival);
        em.flush();
        if (published) {
            em.createNativeQuery("UPDATE festival SET published_at = :at WHERE id = :id")
                    .setParameter("at", Instant.parse("2026-04-01T00:00:00Z"))
                    .setParameter("id", festival.getId())
                    .executeUpdate();
        }
    }

    private void assertProjection(
            HostCoverageRow row,
            boolean hasUnpublishedFestival,
            boolean hasCurrentFestival
    ) {
        assertThat(row).isNotNull();
        assertThat(row.getHasUnpublishedFestival()).isEqualTo(hasUnpublishedFestival);
        assertThat(row.getHasCurrentFestival()).isEqualTo(hasCurrentFestival);
    }
}
