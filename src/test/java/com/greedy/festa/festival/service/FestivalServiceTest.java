package com.greedy.festa.festival.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.festival.dto.FestivalDetailResponse;
import com.greedy.festa.festival.dto.FestivalDetailResponse.LineupArtistResponse;
import com.greedy.festa.festival.dto.FestivalDetailResponse.LineupDayResponse;
import com.greedy.festa.festival.dto.FestivalListItemResponse;
import com.greedy.festa.festival.dto.FestivalRecentResponse;
import com.greedy.festa.festival.dto.FestivalSortType;
import com.greedy.festa.festival.dto.FestivalStatus;
import com.greedy.festa.festival.dto.FestivalUpcomingResponse;
import com.greedy.festa.festival.dto.HostSummaryResponse;
import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({JpaConfig.class, FestivalService.class, FestivalServiceTest.FixedClockConfig.class})
class FestivalServiceTest extends PostgresTestSupport {

    /**
     * UTC로는 2026-05-20, KST로는 2026-05-21인 순간에 시계를 고정한다.
     * 기준 타임존을 걸지 않으면 「종료되지 않음」 판정이 하루 어긋나는 구간이 바로 여기다
     * (KST 00~09시). ClockConfig가 systemUTC()이고 DB 타임존도 고정돼 있지 않다.
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

    @Autowired
    private EntityManagerFactory emf;

    @Test
    void upcoming은_종료되지_않은_축제를_시작일_오름차순으로_준다() {
        // given - 진행 중인 축제도 「다가오는」에 포함된다. 시작일로 거르면 이것이 사라진다
        발행된_축제("이미_끝남", "2026-05-10", "2026-05-19", "2026-05-01T00:00:00Z");
        발행된_축제("진행_중", "2026-05-18", "2026-05-25", "2026-05-01T00:00:00Z");
        발행된_축제("다음_주", "2026-05-30", "2026-06-01", "2026-05-01T00:00:00Z");
        발행된_축제("다음_달", "2026-06-10", "2026-06-12", "2026-05-01T00:00:00Z");
        비운다();

        // when
        List<FestivalUpcomingResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then
        assertThat(결과).extracting(FestivalUpcomingResponse::name)
                .containsExactly("진행_중", "다음_주", "다음_달");
    }

    @Test
    void upcoming의_종료_판정은_한국_시간의_오늘을_기준으로_한다() {
        // given - 시계는 UTC 2026-05-20, KST 2026-05-21에 멈춰 있다.
        // UTC의 오늘(05-20)을 기준으로 삼으면 「어제_종료」가 경계에 걸려 함께 나온다
        발행된_축제("어제_종료", "2026-05-15", "2026-05-20", "2026-05-01T00:00:00Z");
        발행된_축제("오늘_종료", "2026-05-15", "2026-05-21", "2026-05-01T00:00:00Z");
        비운다();

        // when
        List<FestivalUpcomingResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then - 폐막일 당일은 아직 종료가 아니다 (endDate >= today)
        assertThat(결과).extracting(FestivalUpcomingResponse::name).containsExactly("오늘_종료");
    }

    @Test
    void upcoming은_발행된_축제만_준다() {
        // given - 검수 중인 데이터가 홈에 새면 안 된다
        미발행_축제("검수_중", "2026-06-01", "2026-06-03");
        발행된_축제("발행됨", "2026-06-01", "2026-06-03", "2026-05-01T00:00:00Z");
        비운다();

        // when
        List<FestivalUpcomingResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then
        assertThat(결과).extracting(FestivalUpcomingResponse::name).containsExactly("발행됨");
    }

    @Test
    void 카드는_축제와_주최_정보를_담는다() {
        // given
        Host 주최 = Host.builder()
                .name("테스트_성균관대학교")
                .region("Seoul")
                .logoUrl("https://cdn.example.com/logo.png")
                .build();
        em.persist(주최);
        Festival 축제 = Festival.builder()
                .host(주최)
                .name("대동제")
                .startDate(날짜("2026-05-30"))
                .endDate(날짜("2026-06-01"))
                .posterUrl("https://cdn.example.com/poster.jpg")
                .venueName("성균관대학교 인문사회과학 캠퍼스")
                .build();
        축제.publish(Instant.parse("2026-05-01T00:00:00Z"));
        em.persist(축제);
        비운다();

        // when
        List<FestivalUpcomingResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then
        FestivalUpcomingResponse 카드 = 결과.getFirst();
        assertThat(카드.festivalId()).isEqualTo(축제.getId());
        assertThat(카드.name()).isEqualTo("대동제");
        assertThat(카드.startDate()).isEqualTo(날짜("2026-05-30"));
        assertThat(카드.endDate()).isEqualTo(날짜("2026-06-01"));
        assertThat(카드.posterUrl()).isEqualTo("https://cdn.example.com/poster.jpg");
        assertThat(카드.venueName()).isEqualTo("성균관대학교 인문사회과학 캠퍼스");
        assertThat(카드.host().id()).isEqualTo(주최.getId());
        assertThat(카드.host().name()).isEqualTo("테스트_성균관대학교");
        assertThat(카드.host().logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
    }

    @Test
    void recent는_발행_시각_역순이고_동점이면_id_역순이다() {
        // given - 일괄 발행이 배치 전체에 같은 Instant를 넣는다(FestivalPublishService).
        // 동점 tiebreaker가 없으면 이 구간의 순서가 호출마다 달라진다
        발행된_축제("가장_먼저_발행", "2026-06-01", "2026-06-03", "2026-05-01T00:00:00Z");
        발행된_축제("일괄_발행_1", "2026-06-01", "2026-06-03", "2026-05-10T00:00:00Z");
        발행된_축제("일괄_발행_2", "2026-06-01", "2026-06-03", "2026-05-10T00:00:00Z");
        비운다();

        // when
        List<FestivalRecentResponse> 결과 = festivalService.getRecentPublished(10);

        // then
        assertThat(결과).extracting(FestivalRecentResponse::name)
                .containsExactly("일괄_발행_2", "일괄_발행_1", "가장_먼저_발행");
    }

    @Test
    void recent는_미발행_축제를_주지_않는다() {
        // given - publishedAt DESC는 Postgres에서 NULLS FIRST가 기본이다.
        // 필터를 빼면 미발행 축제가 목록 맨 앞을 차지한다
        미발행_축제("검수_중", "2026-06-01", "2026-06-03");
        발행된_축제("발행됨", "2026-06-01", "2026-06-03", "2026-05-01T00:00:00Z");
        비운다();

        // when
        List<FestivalRecentResponse> 결과 = festivalService.getRecentPublished(10);

        // then
        assertThat(결과).extracting(FestivalRecentResponse::name).containsExactly("발행됨");
    }

    @Test
    void recent는_이미_종료된_축제도_준다() {
        // given - 「최근 등록된 축제」는 등록 시각 기준이지 진행 상태 기준이 아니다.
        // 과거 라인업 아카이브가 서비스 성격이라 종료된 축제가 뒤늦게 등록되기도 한다
        발행된_축제("끝난_축제", "2026-05-10", "2026-05-19", "2026-05-15T00:00:00Z");
        비운다();

        // when
        List<FestivalRecentResponse> 결과 = festivalService.getRecentPublished(10);

        // then
        assertThat(결과).extracting(FestivalRecentResponse::name).containsExactly("끝난_축제");
    }

    @Test
    void limit은_결과_개수를_제한한다() {
        // given
        발행된_축제("첫째", "2026-06-01", "2026-06-03", "2026-05-01T00:00:00Z");
        발행된_축제("둘째", "2026-06-05", "2026-06-07", "2026-05-01T00:00:00Z");
        발행된_축제("셋째", "2026-06-09", "2026-06-11", "2026-05-01T00:00:00Z");
        비운다();

        // when
        List<FestivalUpcomingResponse> 결과 = festivalService.getUpcomingFestivals(2);

        // then
        assertThat(결과).extracting(FestivalUpcomingResponse::name).containsExactly("첫째", "둘째");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    void upcoming의_limit이_1에서_50_밖이면_거절한다(int limit) {
        // when
        FestaException 예외 = catchThrowableOfType(
                () -> festivalService.getUpcomingFestivals(limit), FestaException.class);

        // then
        assertThat(예외).isNotNull();
        assertThat(예외.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_LIMIT);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31})
    void recent의_limit이_1에서_30_밖이면_거절한다(int limit) {
        // given - upcoming과 상한이 다르다. 프론트 계약이 recent만 30이다
        // when
        FestaException 예외 = catchThrowableOfType(
                () -> festivalService.getRecentPublished(limit), FestaException.class);

        // then
        assertThat(예외).isNotNull();
        assertThat(예외.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_LIMIT);
    }

    @Test
    void 두_엔드포인트의_상한값은_통과한다() {
        // given - 상한 둘이 다른 숫자라 한쪽을 복사해 쓰면 조용히 어긋난다
        // when & then
        assertThatCode(() -> festivalService.getUpcomingFestivals(50)).doesNotThrowAnyException();
        assertThatCode(() -> festivalService.getRecentPublished(30)).doesNotThrowAnyException();
    }

    @Test
    void 축제가_늘어도_쿼리는_한_번이다() {
        // given - 카드가 host를 싣는다. 함께 가져오지 않으면 축제 수만큼 쿼리가 붙는다
        발행된_축제("첫째", "2026-06-01", "2026-06-03", "2026-05-01T00:00:00Z");
        발행된_축제("둘째", "2026-06-05", "2026-06-07", "2026-05-01T00:00:00Z");
        발행된_축제("셋째", "2026-06-09", "2026-06-11", "2026-05-01T00:00:00Z");
        비운다();

        Statistics 통계 = emf.unwrap(SessionFactory.class).getStatistics();
        통계.clear();

        // when
        List<FestivalUpcomingResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then
        assertThat(결과).hasSize(3);
        assertThat(통계.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void 발행되지_않은_축제는_목록에서_빠진다() {
        // given
        Host 주최 = 주최("테스트_연세대학교");
        축제(주최, "발행됨", 날짜(2026, 6, 1), 날짜(2026, 6, 3), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "미발행", 날짜(2026, 6, 1), 날짜(2026, 6, 3), null);
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalSortType.LATEST);

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
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalSortType.LATEST);

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
                목록(null, null, 아티스트.getId(), FestivalSortType.LATEST);

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
                목록(null, null, 아티스트.getId(), FestivalSortType.LATEST);

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
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalSortType.LATEST);

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
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalSortType.UPCOMING);

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
                목록(이_주최.getId(), null, null, FestivalSortType.LATEST);

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
                목록(null, 2026, null, FestivalSortType.UPCOMING);

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
        PageResponse<FestivalListItemResponse> 결과 = 목록(null, null, null, FestivalSortType.LATEST);

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
                () -> festivalService.getFestivals(null, null, null, null, null, FestivalSortType.LATEST, -1, 20),
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
                () -> festivalService.getFestivals(null, null, null, null, null, FestivalSortType.LATEST, 0, size),
                FestaException.class
        );

        // then
        assertThat(예외.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE);
    }

    @Test
    void status_UPCOMING은_시작일이_오늘보다_뒤인_축제만_남긴다() {
        // given - 고정된 시계의 KST 오늘은 2026-05-21이다
        Host 주최 = 주최("테스트_서강대학교");
        축제(주최, "내일 시작", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "오늘 시작", 날짜(2026, 5, 21), 날짜(2026, 5, 23), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "어제 종료", 날짜(2026, 5, 18), 날짜(2026, 5, 20), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, null, FestivalStatus.UPCOMING, null, FestivalSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("내일 시작");
    }

    @Test
    void status_ONGOING은_시작일과_종료일을_포함해_오늘이_기간에_든_축제를_남긴다() {
        // given
        Host 주최 = 주최("테스트_성균관대학교");
        축제(주최, "오늘 시작", 날짜(2026, 5, 21), 날짜(2026, 5, 23), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "오늘 종료", 날짜(2026, 5, 19), 날짜(2026, 5, 21), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "한창", 날짜(2026, 5, 20), 날짜(2026, 5, 22), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "내일 시작", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "어제 종료", 날짜(2026, 5, 18), 날짜(2026, 5, 20), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, null, FestivalStatus.ONGOING, null, FestivalSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactlyInAnyOrder("오늘 시작", "오늘 종료", "한창");
    }

    @Test
    void status_ENDED는_종료일이_오늘보다_앞인_축제만_남긴다() {
        // given
        Host 주최 = 주최("테스트_홍익대학교");
        축제(주최, "어제 종료", 날짜(2026, 5, 18), 날짜(2026, 5, 20), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "오늘 종료", 날짜(2026, 5, 19), 날짜(2026, 5, 21), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, null, FestivalStatus.ENDED, null, FestivalSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("어제 종료");
    }

    @Test
    void status는_UTC가_아니라_KST의_오늘로_판정한다() {
        // given - 시계는 UTC 2026-05-20 / KST 2026-05-21에 멈춰 있다.
        //         하루짜리 2026-05-21 축제는 KST로는 진행 중이고 UTC로는 아직 시작 전이다
        Host 주최 = 주최("테스트_이화여자대학교");
        축제(주최, "오늘 하루", 날짜(2026, 5, 21), 날짜(2026, 5, 21), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 진행중 =
                목록(null, null, null, FestivalStatus.ONGOING, null, FestivalSortType.LATEST);
        PageResponse<FestivalListItemResponse> 예정 =
                목록(null, null, null, FestivalStatus.UPCOMING, null, FestivalSortType.LATEST);

        // then
        assertThat(진행중.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("오늘 하루");
        assertThat(예정.items()).isEmpty();
    }

    @Test
    void sort의_UPCOMING은_거르지_않고_status만_거른다() {
        // given - 같은 낱말이지만 sort는 순서를 정하고 status는 걸러낸다
        Host 주최 = 주최("테스트_고려대학교");
        축제(주최, "먼 과거", 날짜(2024, 5, 1), 날짜(2024, 5, 3), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "가까운 과거", 날짜(2025, 5, 1), 날짜(2025, 5, 3), 시각("2026-05-02T00:00:00Z"));
        축제(주최, "미래", 날짜(2026, 9, 1), 날짜(2026, 9, 3), 시각("2026-05-03T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, null, FestivalStatus.ENDED, null, FestivalSortType.UPCOMING);

        // then - status가 미래를 걷어내고 sort는 남은 것을 시작일 오름차순으로 놓는다
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("먼 과거", "가까운 과거");
    }

    @Test
    void q는_축제_이름_부분_일치이며_대소문자를_가리지_않는다() {
        // given
        Host 주최 = 주최("테스트_연세대학교");
        축제(주최, "AKARAKA 2026", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "대동제", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, null, null, "akaraka", FestivalSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("AKARAKA 2026");
    }

    @Test
    void q의_퍼센트는_와일드카드가_아니라_리터럴로_검색된다() {
        // given - 이스케이프하지 않으면 LIKE '%%%'가 되어 전체가 나온다
        Host 주최 = 주최("테스트_한국외국어대학교");
        축제(주최, "할인 50% 축제", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "일반 축제", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, null, null, "%", FestivalSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("할인 50% 축제");
    }

    @Test
    void q의_언더바는_와일드카드가_아니라_리터럴로_검색된다() {
        // given - 이스케이프하지 않으면 한 글자 와일드카드가 되어 전체가 나온다
        Host 주최 = 주최("테스트_국민대학교");
        축제(주최, "sub_festival", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "메인축제", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, null, null, "_", FestivalSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactly("sub_festival");
    }

    @Test
    void q가_공백뿐이면_검색어를_주지_않은_것과_같다() {
        // given
        Host 주최 = 주최("테스트_숭실대학교");
        축제(주최, "가나다", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        축제(주최, "라마바", 날짜(2026, 5, 22), 날짜(2026, 5, 24), 시각("2026-05-01T00:00:00Z"));
        비운다();

        // when
        PageResponse<FestivalListItemResponse> 결과 =
                목록(null, null, null, null, "   ", FestivalSortType.LATEST);

        // then
        assertThat(결과.items()).extracting(FestivalListItemResponse::name)
                .containsExactlyInAnyOrder("가나다", "라마바");
    }

    @Test
    void year가_LocalDate_범위를_넘으면_FESTIVAL_INVALID_FILTER로_막힌다() {
        // when
        FestaException 예외 = catchThrowableOfType(
                () -> 목록(null, 2_000_000_000, null, null, null, FestivalSortType.LATEST),
                FestaException.class
        );

        // then
        assertThat(예외.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_FILTER);
    }

    @Test
    void 상세는_히어로와_입장_정보와_좌표를_한_응답에_담는다() {
        // given
        Host 주최 = Host.builder()
                .name("테스트_성균관대학교")
                .region("Seoul")
                .logoUrl("https://cdn.example.com/logo.png")
                .instagramUrl("https://instagram.com/skku")
                .homepageUrl("https://skku.edu")
                .build();
        em.persist(주최);
        Festival 축제 = Festival.builder()
                .host(주최)
                .name("인문사회과학 캠퍼스 대동제")
                .startDate(날짜("2026-05-30"))
                .endDate(날짜("2026-06-01"))
                .posterUrl("https://cdn.example.com/poster.jpg")
                .venueName("성균관대학교 인문사회과학 캠퍼스")
                .address("서울 종로구 성균관로 25-2")
                .latitude(37.5883)
                .longitude(126.9936)
                .externalVisitor(ExternalVisitorPolicy.CONDITIONAL)
                .verification(VerificationMethod.PRE_BOOKING)
                .ticketType(TicketType.PAID)
                .ticketOpenAt(Instant.parse("2026-05-07T01:00:00Z"))
                .admissionNote("재학생 우선존 별도 운영")
                // 공개 응답에 새면 안 되는 값이다 — DTO에 대응하는 필드가 없다는 것이 그 보장이다
                .admissionRaw("외부인 입장 가능(사전예약 필수), 티켓 유료")
                .build();
        발행한다(축제);
        비운다();

        // when
        FestivalDetailResponse 결과 = festivalService.getFestivalDetail(축제.getId());

        // then
        assertThat(결과.id()).isEqualTo(축제.getId());
        assertThat(결과.name()).isEqualTo("인문사회과학 캠퍼스 대동제");
        assertThat(결과.startDate()).isEqualTo(날짜("2026-05-30"));
        assertThat(결과.endDate()).isEqualTo(날짜("2026-06-01"));
        assertThat(결과.posterUrl()).isEqualTo("https://cdn.example.com/poster.jpg");

        assertThat(결과.host().id()).isEqualTo(주최.getId());
        assertThat(결과.host().name()).isEqualTo("테스트_성균관대학교");
        assertThat(결과.host().logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(결과.host().instagramUrl()).isEqualTo("https://instagram.com/skku");
        assertThat(결과.host().homepageUrl()).isEqualTo("https://skku.edu");

        assertThat(결과.admission().externalVisitor()).isEqualTo(ExternalVisitorPolicy.CONDITIONAL);
        assertThat(결과.admission().verification()).isEqualTo(VerificationMethod.PRE_BOOKING);
        assertThat(결과.admission().ticketType()).isEqualTo(TicketType.PAID);
        assertThat(결과.admission().ticketOpenAt()).isEqualTo(Instant.parse("2026-05-07T01:00:00Z"));
        assertThat(결과.admission().note()).isEqualTo("재학생 우선존 별도 운영");

        assertThat(결과.location().venueName()).isEqualTo("성균관대학교 인문사회과학 캠퍼스");
        assertThat(결과.location().address()).isEqualTo("서울 종로구 성균관로 25-2");
        assertThat(결과.location().latitude()).isEqualTo(37.5883);
        assertThat(결과.location().longitude()).isEqualTo(126.9936);
    }

    @Test
    void 라인업은_day별로_묶이고_각_day의_날짜는_시작일에서_파생된다() {
        // given
        Festival 축제 = 주최명으로_발행된_축제("테스트_연세대학교", "2026-06-01", "2026-06-03");
        라인업(축제, 아티스트("아이유"), 1, 1);
        라인업(축제, 아티스트("잔나비"), 1, 2);
        라인업(축제, 아티스트("10CM"), 3, 1);
        비운다();

        // when
        FestivalDetailResponse 결과 = festivalService.getFestivalDetail(축제.getId());

        // then - day 2는 라인업이 없어 응답에 담기지 않는다
        assertThat(결과.lineup()).extracting(LineupDayResponse::day).containsExactly(1, 3);
        assertThat(결과.lineup()).extracting(LineupDayResponse::date)
                .containsExactly(날짜("2026-06-01"), 날짜("2026-06-03"));
        assertThat(결과.lineup().get(0).artists()).hasSize(2);
        assertThat(결과.lineup().get(1).artists()).hasSize(1);
    }

    @Test
    void 미공개_아티스트는_자리를_유지한_채_필드가_모두_null이다() {
        // given - 아직 공개 안 된 것과 라인업이 작은 것은 사용자에게 다른 정보다
        Festival 축제 = 주최명으로_발행된_축제("테스트_고려대학교", "2026-06-01", "2026-06-03");
        라인업(축제, 아티스트("청하"), 1, 1);
        라인업(축제, null, 1, 2);
        라인업(축제, 아티스트("크러쉬"), 1, 3);
        비운다();

        // when
        FestivalDetailResponse 결과 = festivalService.getFestivalDetail(축제.getId());

        // then - 시크릿 자리가 빠지면 3팀짜리 라인업이 2팀으로 보인다
        List<LineupArtistResponse> 아티스트들 = 결과.lineup().get(0).artists();
        assertThat(아티스트들).hasSize(3);

        LineupArtistResponse 시크릿 = 아티스트들.get(1);
        assertThat(시크릿.id()).isNull();
        assertThat(시크릿.name()).isNull();
        assertThat(시크릿.imageUrl()).isNull();
        assertThat(시크릿.genre()).isNull();
    }

    @Test
    void 라인업은_day_오름차순_그리고_displayOrder_오름차순으로_나온다() {
        // given - 저장 순서를 일부러 뒤섞는다. 배열 순서가 곧 계약이라 정렬은 쿼리가 책임진다
        Festival 축제 = 주최명으로_발행된_축제("테스트_한양대학교", "2026-06-01", "2026-06-03");
        라인업(축제, 아티스트("2일차 두번째"), 2, 2);
        라인업(축제, 아티스트("1일차 두번째"), 1, 2);
        라인업(축제, 아티스트("2일차 첫번째"), 2, 1);
        라인업(축제, 아티스트("1일차 첫번째"), 1, 1);
        비운다();

        // when
        FestivalDetailResponse 결과 = festivalService.getFestivalDetail(축제.getId());

        // then
        assertThat(결과.lineup().get(0).artists()).extracting(LineupArtistResponse::name)
                .containsExactly("1일차 첫번째", "1일차 두번째");
        assertThat(결과.lineup().get(1).artists()).extracting(LineupArtistResponse::name)
                .containsExactly("2일차 첫번째", "2일차 두번째");
    }

    @ParameterizedTest
    @CsvSource({"2026-05-23, 2", "2026-05-21, 0", "2026-05-19, -2"})
    void dday는_한국_시간의_오늘을_기준으로_계산되고_양수가_남은_일수다(String 시작일, int 예상) {
        // given - 시계는 UTC 2026-05-20, KST 2026-05-21에 멈춰 있다.
        // UTC를 기준으로 삼으면 세 경우 모두 1씩 커진다
        Festival 축제 = 주최명으로_발행된_축제("테스트_서강대학교_" + 시작일, 시작일, "2026-06-30");
        비운다();

        // when
        FestivalDetailResponse 결과 = festivalService.getFestivalDetail(축제.getId());

        // then
        assertThat(결과.dday()).isEqualTo(예상);
    }

    @Test
    void 발행되지_않은_축제는_상세에서도_404다() {
        // given - 검수 중인 데이터가 공개 응답으로 새면 안 된다
        Festival 축제 = Festival.builder()
                .host(주최("테스트_중앙대학교"))
                .name("미발행")
                .startDate(날짜("2026-06-01"))
                .endDate(날짜("2026-06-03"))
                .build();
        em.persist(축제);
        비운다();

        // when
        FestaException 예외 = catchThrowableOfType(
                () -> festivalService.getFestivalDetail(축제.getId()), FestaException.class);

        // then
        assertThat(예외).isNotNull();
        assertThat(예외.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
    }

    @Test
    void 주최가_연결되지_않은_축제는_상세에서도_404다() {
        // given - 발행 조건이 주최 연결을 요구하므로 이런 행은 원래 없어야 한다.
        // 걸러내지 않으면 host 매핑에서 NPE가 나 404가 아니라 500으로 터진다
        Festival 축제 = Festival.builder()
                .name("주최 없음")
                .startDate(날짜("2026-06-01"))
                .endDate(날짜("2026-06-03"))
                .build();
        발행한다(축제);
        비운다();

        // when
        FestaException 예외 = catchThrowableOfType(
                () -> festivalService.getFestivalDetail(축제.getId()), FestaException.class);

        // then
        assertThat(예외).isNotNull();
        assertThat(예외.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_축제는_404다() {
        // when
        FestaException 예외 = catchThrowableOfType(
                () -> festivalService.getFestivalDetail(9_999_999L), FestaException.class);

        // then
        assertThat(예외).isNotNull();
        assertThat(예외.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_NOT_FOUND);
    }

    @Test
    void 라인업이_늘어도_쿼리는_두_번이다() {
        // given - 축제+주최 한 번, 라인업+아티스트 한 번.
        // 아티스트를 함께 가져오지 않으면 라인업 수만큼 쿼리가 붙는다
        Festival 축제 = 주최명으로_발행된_축제("테스트_경희대학교", "2026-06-01", "2026-06-03");
        라인업(축제, 아티스트("아티스트1"), 1, 1);
        라인업(축제, 아티스트("아티스트2"), 1, 2);
        라인업(축제, 아티스트("아티스트3"), 1, 3);
        라인업(축제, null, 1, 4);
        라인업(축제, 아티스트("아티스트5"), 2, 1);
        비운다();

        Statistics 통계 = emf.unwrap(SessionFactory.class).getStatistics();
        통계.clear();

        // when
        FestivalDetailResponse 결과 = festivalService.getFestivalDetail(축제.getId());

        // then
        assertThat(결과.lineup()).hasSize(2);
        assertThat(통계.getPrepareStatementCount()).isEqualTo(2);
    }

    private void 비운다() {
        em.flush();
        em.clear();
    }

    private LocalDate 날짜(String value) {
        return LocalDate.parse(value);
    }

    private Host 주최(String name) {
        Host host = Host.builder().name(name).region("Seoul").build();
        em.persist(host);
        return host;
    }

    private Festival 발행된_축제(String 이름, String 시작일, String 종료일, String 발행시각) {
        Festival festival = 축제(이름, 시작일, 종료일);
        festival.publish(Instant.parse(발행시각));
        em.persist(festival);
        return festival;
    }

    private Festival 미발행_축제(String 이름, String 시작일, String 종료일) {
        Festival festival = 축제(이름, 시작일, 종료일);
        em.persist(festival);
        return festival;
    }

    private Festival 축제(String 이름, String 시작일, String 종료일) {
        return Festival.builder()
                .host(주최("테스트_" + 이름))
                .name(이름)
                .startDate(날짜(시작일))
                .endDate(날짜(종료일))
                .build();
    }

    private PageResponse<FestivalListItemResponse> 목록(
            Long hostId, Integer year, Long artistId, FestivalSortType sort
    ) {
        return 목록(hostId, year, artistId, null, null, sort);
    }

    private PageResponse<FestivalListItemResponse> 목록(
            Long hostId, Integer year, Long artistId, FestivalStatus status, String q,
            FestivalSortType sort
    ) {
        return festivalService.getFestivals(hostId, year, artistId, status, q, sort, 0, 20);
    }

    private LocalDate 날짜(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private Instant 시각(String value) {
        return Instant.parse(value);
    }

    private Artist 아티스트(String name) {
        Artist artist = Artist.builder().name(name).genre(ArtistGenre.BAND).build();
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

    private Festival 주최명으로_발행된_축제(String 주최명, String 시작일, String 종료일) {
        Festival festival = Festival.builder()
                .host(주최(주최명))
                .name(주최명 + " 축제")
                .startDate(날짜(시작일))
                .endDate(날짜(종료일))
                .build();
        발행한다(festival);
        return festival;
    }

    private void 발행한다(Festival festival) {
        festival.publish(Instant.parse("2026-05-01T00:00:00Z"));
        em.persist(festival);
    }

}
