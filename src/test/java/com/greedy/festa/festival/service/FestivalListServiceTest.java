package com.greedy.festa.festival.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.festival.dto.FestivalListItemResponse;
import com.greedy.festa.festival.dto.FestivalListSortType;
import com.greedy.festa.festival.dto.HostSummaryResponse;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaConfig.class, FestivalService.class, FestivalListServiceTest.FixedClockConfig.class})
class FestivalListServiceTest extends PostgresTestSupport {

    /**
     * 목록 조회는 「오늘」을 쓰지 않지만 FestivalService가 Clock을 요구한다 (#81).
     * 정렬·필터 결과가 실행 시각에 흔들리지 않도록 고정한다.
     */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-05-20T15:30:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private FestivalService festivalService;

    @Autowired
    private EntityManager em;

    @Test
    void 발행되지_않은_축제는_목록에서_빠진다() {
        // given
        Host 주최 = 주최("테스트_연세대학교");
        축제(주최, "발행됨", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "미발행", 날짜(2026, 6, 1), 날짜(2026, 6, 3), null);
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalListSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name).containsExactly("발행됨");
        assertThat(결과.totalElements()).isEqualTo(1);
    }

    @Test
    void 주최가_연결되지_않은_축제는_목록에서_빠진다() {
        // given - 발행 조건이 주최 연결을 요구하므로 이런 행은 원래 존재하면 안 된다
        Host 주최 = 주최("테스트_고려대학교");
        축제(주최, "주최 있음", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        축제(null, "주최 없음", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalListSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name).containsExactly("주최 있음");
        assertThat(결과.totalElements()).isEqualTo(1);
    }

    @Test
    void 같은_아티스트가_여러_day에_출연해도_축제는_한_번만_나온다() {
        // given
        Host 주최 = 주최("테스트_성균관대학교");
        Festival 축제 = 축제(주최, "이틀 출연", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        Artist 아티스트 = 아티스트("아이유");
        라인업(축제, 아티스트, 1, 0);
        라인업(축제, 아티스트, 2, 0);
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, 아티스트.getId(), FestivalListSortType.LATEST);

        // then
        assertThat(결과.items()).hasSize(1);
        assertThat(결과.totalElements()).isEqualTo(1);
    }

    @Test
    void artistId를_주면_그_아티스트가_출연하지_않은_축제는_빠진다() {
        // given
        Host 주최 = 주최("테스트_서강대학교");
        Festival 출연한_축제 =
                축제(주최, "출연함", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "출연 안 함", 날짜(2026, 7, 1), 날짜(2026, 7, 3), 시각("2026-05-02T00:00:00Z"));
        Artist 아티스트 = 아티스트("잔나비");
        라인업(출연한_축제, 아티스트, 1, 0);
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, 아티스트.getId(), FestivalListSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name).containsExactly("출연함");
    }

    @Test
    void LATEST는_발행_시각_역순이고_동점이면_id_오름차순이다() {
        // given
        Host 주최 = 주최("테스트_한양대학교");
        Instant 같은_시각 = 시각("2026-05-01T00:00:00Z");
        축제(주최, "동점 먼저", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 같은_시각);
        축제(주최, "동점 나중", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 같은_시각);
        축제(주최, "가장 최근 발행", 날짜(2026, 9, 1), 날짜(2026, 9, 3), 시각("2026-05-02T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalListSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("가장 최근 발행", "동점 먼저", "동점 나중");
    }

    @Test
    void UPCOMING은_시작일_오름차순으로_정렬만_하고_지난_축제를_걸러내지_않는다() {
        // given - 과거 축제만 남는 주최별 이력 화면이 통째로 비면 안 된다
        Host 주최 = 주최("테스트_중앙대학교");
        축제(주최, "미래", 날짜(2026, 9, 1), 날짜(2026, 9, 3), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "과거", 날짜(2024, 5, 1), 날짜(2024, 5, 3), 시각("2026-05-02T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalListSortType.UPCOMING);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("과거", "미래");
    }

    @Test
    void hostId를_주면_그_주최의_축제만_나온다() {
        // given
        Host 이_주최 = 주최("테스트_건국대학교");
        Host 다른_주최 = 주최("테스트_동국대학교");
        축제(이_주최, "이쪽", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        축제(다른_주최, "저쪽", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(이_주최.getId(), null, null, FestivalListSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name).containsExactly("이쪽");
    }

    @Test
    void year는_시작일이_그_해에_속한_축제만_남기고_경계를_넘지_않는다() {
        // given
        Host 주최 = 주최("테스트_경희대학교");
        축제(주최, "전해 마지막날", 날짜(2025, 12, 31), 날짜(2026, 1, 2), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "첫날", 날짜(2026, 1, 1), 날짜(2026, 1, 3), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "마지막날", 날짜(2026, 12, 31), 날짜(2027, 1, 2), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "다음해 첫날", 날짜(2027, 1, 1), 날짜(2027, 1, 3), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, 2026, null, FestivalListSortType.UPCOMING);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("첫날", "마지막날");
    }

    @Test
    void 응답에_주최_요약이_함께_담긴다() {
        // given
        Host 주최 = Host.builder()
                .name("테스트_숙명여자대학교")
                .region("Seoul")
                .logoUrl("https://cdn.example.com/logo.png")
                .build();
        em.persist(주최);
        축제(주최, "축제", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalListSortType.LATEST);

        // then
        HostSummaryResponse 담긴_주최 = 결과.items().get(0).host();
        assertThat(담긴_주최.id()).isEqualTo(주최.getId());
        assertThat(담긴_주최.name()).isEqualTo("테스트_숙명여자대학교");
        assertThat(담긴_주최.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
    }

    @Test
    void page가_음수면_INVALID_PAGE로_막힌다() {
        // when
        FestaException 예외 = catchThrowableOfType(
                () -> festivalService.getFestivals(null, null, null, FestivalListSortType.LATEST, -1, 20),
                FestaException.class
        );

        // then
        assertThat(예외.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PAGE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    void size가_허용_범위를_벗어나면_INVALID_PAGE_SIZE로_막힌다(int size) {
        // when
        FestaException 예외 = catchThrowableOfType(
                () -> festivalService.getFestivals(null, null, null, FestivalListSortType.LATEST, 0, size),
                FestaException.class
        );

        // then
        assertThat(예외.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE);
    }

    private PageResponse<FestivalListItemResponse> 목록(
            Long hostId, Integer year, Long artistId, FestivalListSortType sort
    ) {
        return festivalService.getFestivals(hostId, year, artistId, sort, 0, 20);
    }

    private void 비운다() {
        em.flush();
        em.clear();
    }

    private LocalDate 날짜(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private Instant 시각(String value) {
        return Instant.parse(value);
    }

    private Host 주최(String name) {
        Host host = Host.builder().name(name).region("Seoul").build();
        em.persist(host);
        return host;
    }

    private Artist 아티스트(String name) {
        Artist artist = Artist.builder().name(name).build();
        em.persist(artist);
        return artist;
    }

    private Festival 축제(
            Host host, String name, LocalDate startDate, LocalDate endDate, Instant publishedAt
    ) {
        Festival festival = Festival.builder()
                .host(host)
                .name(name)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        if (publishedAt != null) {
            festival.publish(publishedAt);
        }
        em.persist(festival);
        return festival;
    }

    private void 라인업(Festival festival, Artist artist, int day, int displayOrder) {
        em.persist(Lineup.builder()
                .festival(festival)
                .artist(artist)
                .day(day)
                .displayOrder(displayOrder)
                .build());
    }
}
