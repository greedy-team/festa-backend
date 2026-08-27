package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalCardResponse;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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
        List<FestivalCardResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then
        assertThat(결과).extracting(FestivalCardResponse::name)
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
        List<FestivalCardResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then - 폐막일 당일은 아직 종료가 아니다 (endDate >= today)
        assertThat(결과).extracting(FestivalCardResponse::name).containsExactly("오늘_종료");
    }

    @Test
    void upcoming은_발행된_축제만_준다() {
        // given - 검수 중인 데이터가 홈에 새면 안 된다
        미발행_축제("검수_중", "2026-06-01", "2026-06-03");
        발행된_축제("발행됨", "2026-06-01", "2026-06-03", "2026-05-01T00:00:00Z");
        비운다();

        // when
        List<FestivalCardResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then
        assertThat(결과).extracting(FestivalCardResponse::name).containsExactly("발행됨");
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
        List<FestivalCardResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then
        FestivalCardResponse 카드 = 결과.getFirst();
        assertThat(카드.festivalId()).isEqualTo(축제.getId());
        assertThat(카드.name()).isEqualTo("대동제");
        assertThat(카드.startDate()).isEqualTo(날짜("2026-05-30"));
        assertThat(카드.endDate()).isEqualTo(날짜("2026-06-01"));
        assertThat(카드.posterUrl()).isEqualTo("https://cdn.example.com/poster.jpg");
        assertThat(카드.host().id()).isEqualTo(주최.getId());
        assertThat(카드.host().name()).isEqualTo("테스트_성균관대학교");
        assertThat(카드.host().logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
    }

    @Test
    void recent는_발행_시각_역순이고_동점이면_id_역순이다() {
        // given - 일괄 발행이 배치 전체에 같은 Instant를 넣는다(FestivalAdminService).
        // 동점 tiebreaker가 없으면 이 구간의 순서가 호출마다 달라진다
        발행된_축제("가장_먼저_발행", "2026-06-01", "2026-06-03", "2026-05-01T00:00:00Z");
        발행된_축제("일괄_발행_1", "2026-06-01", "2026-06-03", "2026-05-10T00:00:00Z");
        발행된_축제("일괄_발행_2", "2026-06-01", "2026-06-03", "2026-05-10T00:00:00Z");
        비운다();

        // when
        List<FestivalCardResponse> 결과 = festivalService.getRecentPublished(10);

        // then
        assertThat(결과).extracting(FestivalCardResponse::name)
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
        List<FestivalCardResponse> 결과 = festivalService.getRecentPublished(10);

        // then
        assertThat(결과).extracting(FestivalCardResponse::name).containsExactly("발행됨");
    }

    @Test
    void recent는_이미_종료된_축제도_준다() {
        // given - 「최근 등록된 축제」는 등록 시각 기준이지 진행 상태 기준이 아니다.
        // 과거 라인업 아카이브가 서비스 성격이라 종료된 축제가 뒤늦게 등록되기도 한다
        발행된_축제("끝난_축제", "2026-05-10", "2026-05-19", "2026-05-15T00:00:00Z");
        비운다();

        // when
        List<FestivalCardResponse> 결과 = festivalService.getRecentPublished(10);

        // then
        assertThat(결과).extracting(FestivalCardResponse::name).containsExactly("끝난_축제");
    }

    @Test
    void limit은_결과_개수를_제한한다() {
        // given
        발행된_축제("첫째", "2026-06-01", "2026-06-03", "2026-05-01T00:00:00Z");
        발행된_축제("둘째", "2026-06-05", "2026-06-07", "2026-05-01T00:00:00Z");
        발행된_축제("셋째", "2026-06-09", "2026-06-11", "2026-05-01T00:00:00Z");
        비운다();

        // when
        List<FestivalCardResponse> 결과 = festivalService.getUpcomingFestivals(2);

        // then
        assertThat(결과).extracting(FestivalCardResponse::name).containsExactly("첫째", "둘째");
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
        List<FestivalCardResponse> 결과 = festivalService.getUpcomingFestivals(10);

        // then
        assertThat(결과).hasSize(3);
        assertThat(통계.getPrepareStatementCount()).isEqualTo(1);
    }

    private void 비운다() {
        em.flush();
        em.clear();
    }

    private LocalDate 날짜(String value) {
        return LocalDate.parse(value);
    }

    private Host 주최(String name) {
        Host host = Host.builder().name("테스트_" + name).region("Seoul").build();
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
                .host(주최(이름))
                .name(이름)
                .startDate(날짜(시작일))
                .endDate(날짜(종료일))
                .build();
    }
}
