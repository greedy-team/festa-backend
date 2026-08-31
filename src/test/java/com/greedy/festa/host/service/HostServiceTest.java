package com.greedy.festa.host.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.dto.HostDetailResponse.FrequentArtist;
import com.greedy.festa.host.dto.HostDetailResponse.HistoryItem;
import com.greedy.festa.host.dto.HostDetailResponse.UpcomingFestival;
import com.greedy.festa.host.dto.HostDetailResponse;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaConfig.class, HostService.class, HostServiceTest.고정된_시계.class})
class HostServiceTest extends PostgresTestSupport {

    // UTC로는 2026-05-20이고 KST로는 2026-05-21인 시각을 일부러 골랐다.
    // "오늘"을 KST로 고정하지 않으면 아래 단언이 하루씩 어긋나므로,
    // 모든 테스트가 타임존 회귀 테스트를 겸한다.
    private static final Instant 기준시각 = Instant.parse("2026-05-20T15:30:00Z");

    @TestConfiguration
    static class 고정된_시계 {
        @Bean
        Clock clock() {
            return Clock.fixed(기준시각, ZoneOffset.UTC);
        }
    }

    @Autowired
    private HostService hostService;

    @Autowired
    private EntityManager em;

    private Host 주최;
    private Artist 자주온;
    private Artist 한번온;

    @BeforeEach
    void setUp() {
        주최 = em.merge(Host.builder()
                .name("테스트대학교")
                .shortName("테스트대")
                .region("서울 광진구")
                .homepageUrl("https://test.ac.kr")
                .build());

        자주온 = 아티스트를_넣는다("자주오는밴드");
        한번온 = 아티스트를_넣는다("한번온밴드");

        Festival 예정 = 축제를_넣는다("예정축제", "2026-05-24", "2026-05-26", true);
        축제를_넣는다("오늘끝나는축제", "2026-05-19", "2026-05-21", true);

        // 종료된 발행 축제 4건 — HISTORY_SIZE(2)로 잘리므로 오래된 2건이 빠진다.
        // 2건만 두면 items.size() == total이 되어 자르기가 검증되지 않는다.
        Festival 어제종료 = 축제를_넣는다("어제끝난축제", "2026-05-17", "2026-05-20", true);
        Festival 작년 = 축제를_넣는다("작년축제", "2025-05-20", "2025-05-22", true);
        축제를_넣는다("재작년축제", "2024-05-20", "2024-05-22", true);
        축제를_넣는다("삼년전축제", "2023-05-20", "2023-05-22", true);

        Festival 미발행과거 = 축제를_넣는다("미발행축제", "2026-01-10", "2026-01-12", false);

        라인업에_올린다(어제종료, 자주온, 1);
        라인업에_올린다(어제종료, null, 2);      // 시크릿 게스트
        라인업에_올린다(작년, 자주온, 1);
        라인업에_올린다(작년, 한번온, 2);
        라인업에_올린다(예정, 자주온, 1);        // 아직 안 끝났으므로 집계 대상이 아니다
        라인업에_올린다(미발행과거, 한번온, 1);  // 미발행이므로 집계 대상이 아니다

        em.flush();
        em.clear();
    }

    @Test
    void 발행된_축제만_상세에_나온다() {
        HostDetailResponse response = hostService.getHostDetail(주최.getId());

        assertSoftly(softly -> {
            softly.assertThat(response.upcomingFestivals())
                    .extracting(UpcomingFestival::name)
                    .doesNotContain("미발행축제");
            softly.assertThat(response.festivalHistory().items())
                    .extracting(HistoryItem::name)
                    .doesNotContain("미발행축제");
            softly.assertThat(response.festivalHistory().total()).isEqualTo(4);
            softly.assertThat(response.availableYears())
                    .containsExactly(2026, 2025, 2024, 2023);
            softly.assertThat(response.homepageUrl()).isEqualTo("https://test.ac.kr");
        });
    }

    @Test
    void 예정과_이력은_종료일로_갈리고_dday는_kst_기준이다() {
        HostDetailResponse response = hostService.getHostDetail(주최.getId());

        assertSoftly(softly -> {
            // 오늘 끝나는 축제는 아직 "다가오는" 쪽이다 (endDate >= 오늘)
            softly.assertThat(response.upcomingFestivals())
                    .extracting(UpcomingFestival::name, UpcomingFestival::dday)
                    .containsExactly(
                            tuple("오늘끝나는축제", -2L),
                            tuple("예정축제", 3L));
            // 이력은 종료된 것만, 최신순 HISTORY_SIZE(2)건이며 total은 자르기 전 개수다
            softly.assertThat(response.festivalHistory().items())
                    .extracting(HistoryItem::name)
                    .containsExactly("어제끝난축제", "작년축제");
        });
    }

    @Test
    void 자주_온_아티스트는_출연이_많은_순이고_시크릿_게스트는_빠진다() {
        HostDetailResponse response = hostService.getHostDetail(주최.getId());

        assertThat(response.frequentArtists())
                .extracting(FrequentArtist::artistId, FrequentArtist::appearanceCount)
                .containsExactly(
                        tuple(자주온.getId(), 2L),
                        tuple(한번온.getId(), 1L));
    }

    @Test
    void 없는_주최는_host_not_found다() {
        FestaException exception = catchThrowableOfType(
                FestaException.class, () -> hostService.getHostDetail(999_999L));

        assertThat(exception.getErrorCode()).isEqualTo(HostErrorCode.HOST_NOT_FOUND);
    }

    private Festival 축제를_넣는다(String name, String 시작, String 종료, boolean 발행) {
        Festival festival = Festival.builder()
                .host(주최)
                .name(name)
                .startDate(LocalDate.parse(시작))
                .endDate(LocalDate.parse(종료))
                .build();
        if (발행) {
            festival.publish(기준시각);
        }
        return em.merge(festival);
    }

    private Artist 아티스트를_넣는다(String name) {
        return em.merge(Artist.builder().name(name).build());
    }

    private void 라인업에_올린다(Festival festival, Artist artist, int displayOrder) {
        em.merge(Lineup.builder()
                .festival(festival)
                .artist(artist)
                .day(1)
                .displayOrder(displayOrder)
                .build());
    }
}
