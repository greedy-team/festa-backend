package com.greedy.festa.festival.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.festival.dto.FestivalDetailResponse;
import com.greedy.festa.festival.dto.FestivalDetailResponse.LineupArtistResponse;
import com.greedy.festa.festival.dto.FestivalDetailResponse.LineupDayResponse;
import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
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
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({JpaConfig.class, FestivalService.class, FestivalDetailServiceTest.FixedClockConfig.class})
class FestivalDetailServiceTest extends PostgresTestSupport {

    /**
     * UTC로는 2026-05-20, KST로는 2026-05-21인 순간에 시계를 고정한다.
     * 기준 타임존을 걸지 않으면 dday가 하루 어긋나는 구간이 바로 여기다 (KST 00~09시).
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
        Festival 축제 = 발행된_축제("테스트_연세대학교", "2026-06-01", "2026-06-03");
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
        Festival 축제 = 발행된_축제("테스트_고려대학교", "2026-06-01", "2026-06-03");
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
        Festival 축제 = 발행된_축제("테스트_한양대학교", "2026-06-01", "2026-06-03");
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
        Festival 축제 = 발행된_축제("테스트_서강대학교_" + 시작일, 시작일, "2026-06-30");
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
        Festival 축제 = 발행된_축제("테스트_경희대학교", "2026-06-01", "2026-06-03");
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

    private Artist 아티스트(String name) {
        Artist artist = Artist.builder().name(name).genre(ArtistGenre.BAND).build();
        em.persist(artist);
        return artist;
    }

    private Festival 발행된_축제(String 주최명, String 시작일, String 종료일) {
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

    private void 라인업(Festival festival, Artist artist, int day, int displayOrder) {
        em.persist(Lineup.builder()
                .festival(festival)
                .artist(artist)
                .day(day)
                .displayOrder(displayOrder)
                .build());
    }
}
