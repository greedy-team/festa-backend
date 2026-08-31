package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistDetailResponse;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaConfig.class, ArtistService.class,
        ArtistServicePostgresIntegrationTest.FixedClockConfig.class})
class ArtistServicePostgresIntegrationTest extends PostgresTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 27);

    @Autowired
    private ArtistService artistService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 실제_PostgreSQL에서_KST_분류와_발행_필터_5건_limit_total을_함께_검증한다() {
        Host host = persist(Host.builder()
                .name("한국대학교")
                .shortName("한국대")
                .region("서울")
                .build());
        Artist artist = persist(Artist.builder()
                .name("BTS")
                .genre(ArtistGenre.DANCE)
                .imageUrl("https://example.com/portrait.jpg")
                .build());
        persist(ArtistAlias.builder().artist(artist).name("방탄소년단").build());

        for (int index = 0; index < 6; index++) {
            Festival festival = festival(host, "예정 축제 " + index,
                    TODAY.plusDays(index + 1L), TODAY.plusDays(index + 2L), true);
            lineup(artist, festival, 1);
        }
        Festival oldHistory = festival(host, "오래된 이력",
                TODAY.minusDays(20), TODAY.minusDays(18), true);
        Festival recentHistory = festival(host, "최근 이력",
                TODAY.minusDays(10), TODAY.minusDays(8), true);
        lineup(artist, oldHistory, 1);
        lineup(artist, recentHistory, 1);

        Festival ongoingWithPastPerformance = festival(host, "진행 중 지난 공연",
                TODAY.minusDays(1), TODAY.plusDays(1), true);
        lineup(artist, ongoingWithPastPerformance, 1);
        Festival unpublished = festival(host, "미발행 예정",
                TODAY.plusDays(1), TODAY.plusDays(2), false);
        lineup(artist, unpublished, 1);
        flushAndClear();

        ArtistDetailResponse response = artistService.findById(artist.getId());

        assertThat(response.otherNames()).containsExactly("방탄소년단");
        assertThat(response.imageUrl()).isNull();
        assertThat(response.upcomingShows().items()).hasSize(5);
        assertThat(response.upcomingShows().total()).isEqualTo(6);
        assertThat(response.upcomingShows().items())
                .extracting(item -> item.name())
                .containsExactly("예정 축제 0", "예정 축제 1", "예정 축제 2", "예정 축제 3", "예정 축제 4");
        assertThat(response.upcomingShows().items().getFirst().dday()).isEqualTo(1);
        assertThat(response.appearances().total()).isEqualTo(2);
        assertThat(response.appearances().items())
                .extracting(item -> item.name())
                .containsExactly("최근 이력", "오래된 이력");
    }

    private Festival festival(
            Host host, String name, LocalDate startDate, LocalDate endDate, boolean published
    ) {
        Festival festival = Festival.builder()
                .host(host)
                .name(name)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        if (published) {
            festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        }
        return persist(festival);
    }

    private void lineup(Artist artist, Festival festival, int day) {
        persist(Lineup.builder()
                .artist(artist)
                .festival(festival)
                .day(day)
                .displayOrder(1)
                .build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock artistClock() {
            return Clock.fixed(Instant.parse("2026-08-26T15:30:00Z"), ZoneOffset.UTC);
        }
    }
}
