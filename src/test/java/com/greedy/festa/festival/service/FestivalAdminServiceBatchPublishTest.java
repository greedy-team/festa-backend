package com.greedy.festa.festival.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.festival.dto.FestivalBatchPublishResponse;
import com.greedy.festa.festival.dto.FestivalPublishFailure;
import com.greedy.festa.festival.dto.FestivalPublishFailureReason;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class FestivalAdminServiceBatchPublishTest extends PostgresTestSupport {

    private static final Instant 지금 = Instant.parse("2026-08-23T09:00:00Z");
    private static final Instant 예전에_발행한_시각 = Instant.parse("2026-07-01T00:00:00Z");
    private static final double 위도 = 37.5509;
    private static final double 경도 = 127.0743;

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private EntityManager em;

    private FestivalAdminService festivalAdminService;
    private Host 주최;
    private Artist 아티스트;
    private int 다음_출연_순서 = 1;

    @BeforeEach
    void setUp() {
        festivalAdminService = new FestivalAdminService(
                festivalRepository, Clock.fixed(지금, ZoneOffset.UTC)
        );
        주최 = em.merge(Host.builder().name("테스트대학교").region("서울 광진구").build());
        아티스트 = 아티스트를_넣는다();
    }

    @Test
    void 모두_충족하면_요청한_축제들이_같은_시각으로_발행된다() {
        // given
        Festival 축제1 = 발행_가능한_축제("세종연회");
        Festival 축제2 = 발행_가능한_축제("대동제");
        반영한다();

        // when
        FestivalBatchPublishResponse response = festivalAdminService.batchPublish(
                List.of(축제1.getId(), 축제2.getId())
        );

        // then
        assertThat(response.publishedIds()).containsExactly(축제1.getId(), 축제2.getId());
        assertThat(response.failed()).isEmpty();
        assertThat(발행시각(축제1)).isEqualTo(지금);
        assertThat(발행시각(축제2)).isEqualTo(지금);
    }

    @Test
    void 라인업이_0건이면_LINEUP_EMPTY로_막히고_나머지는_발행된다() {
        // given
        Festival 온전한_축제 = 발행_가능한_축제("세종연회");
        Festival 라인업이_없는_축제 = 축제를_넣는다("라인업 없음", 주최, 위도, 경도);
        반영한다();

        // when
        FestivalBatchPublishResponse response = festivalAdminService.batchPublish(
                List.of(온전한_축제.getId(), 라인업이_없는_축제.getId())
        );

        // then
        assertThat(response.publishedIds()).containsExactly(온전한_축제.getId());
        assertThat(response.failed())
                .extracting(FestivalPublishFailure::festivalId, FestivalPublishFailure::reason)
                .containsExactly(tuple(
                        라인업이_없는_축제.getId(), FestivalPublishFailureReason.LINEUP_EMPTY
                ));
        assertThat(발행시각(온전한_축제)).isEqualTo(지금);
        assertThat(발행시각(라인업이_없는_축제)).isNull();
    }

    @Test
    void 주최가_연결되지_않으면_HOST_NOT_LINKED로_막힌다() {
        // given
        Festival festival = 축제를_넣는다("주최 없음", null, 위도, 경도);
        라인업에_올린다(festival);
        반영한다();

        // when
        FestivalBatchPublishResponse response =
                festivalAdminService.batchPublish(List.of(festival.getId()));

        // then
        assertThat(response.publishedIds()).isEmpty();
        assertThat(response.failed())
                .extracting(FestivalPublishFailure::reason)
                .containsExactly(FestivalPublishFailureReason.HOST_NOT_LINKED);
        assertThat(발행시각(festival)).isNull();
    }

    @Test
    void 좌표가_없으면_COORDINATES_MISSING으로_막힌다() {
        // given
        Festival festival = 축제를_넣는다("좌표 없음", 주최, null, null);
        라인업에_올린다(festival);
        반영한다();

        // when
        FestivalBatchPublishResponse response =
                festivalAdminService.batchPublish(List.of(festival.getId()));

        // then
        assertThat(response.publishedIds()).isEmpty();
        assertThat(response.failed())
                .extracting(FestivalPublishFailure::reason)
                .containsExactly(FestivalPublishFailureReason.COORDINATES_MISSING);
        assertThat(발행시각(festival)).isNull();
    }

    @Test
    void 위반_조건이_여럿이면_첫_사유_하나만_낸다() {
        // given - 라인업 0건이면서 주최도 좌표도 없다
        Festival festival = 축제를_넣는다("전부 비어있음", null, null, null);
        반영한다();

        // when
        FestivalBatchPublishResponse response =
                festivalAdminService.batchPublish(List.of(festival.getId()));

        // then
        assertThat(response.failed())
                .extracting(FestivalPublishFailure::reason)
                .containsExactly(FestivalPublishFailureReason.LINEUP_EMPTY);
    }

    @Test
    void 존재하지_않는_id는_NOT_FOUND로_담기고_나머지_발행을_막지_않는다() {
        // given
        Festival festival = 발행_가능한_축제("세종연회");
        반영한다();
        long 없는_id = festival.getId() + 10_000L;

        // when
        FestivalBatchPublishResponse response =
                festivalAdminService.batchPublish(List.of(festival.getId(), 없는_id));

        // then
        assertThat(response.publishedIds()).containsExactly(festival.getId());
        assertThat(response.failed())
                .extracting(FestivalPublishFailure::festivalId, FestivalPublishFailure::reason)
                .containsExactly(tuple(없는_id, FestivalPublishFailureReason.NOT_FOUND));
    }

    @Test
    void 이미_발행된_축제는_시각을_유지한_채_성공으로_담긴다() {
        // given
        Festival festival = 발행_가능한_축제("세종연회");
        festival.publish(예전에_발행한_시각);
        반영한다();

        // when
        FestivalBatchPublishResponse response =
                festivalAdminService.batchPublish(List.of(festival.getId()));

        // then
        assertThat(response.publishedIds()).containsExactly(festival.getId());
        assertThat(response.failed()).isEmpty();
        assertThat(발행시각(festival)).isEqualTo(예전에_발행한_시각);
    }

    @Test
    void 이미_발행된_축제는_게이트를_어겨도_성공으로_담긴다() {
        // given - 라인업도 좌표도 없지만 이미 발행돼 있다
        Festival festival = 축제를_넣는다("이미 발행", null, null, null);
        festival.publish(예전에_발행한_시각);
        반영한다();

        // when
        FestivalBatchPublishResponse response =
                festivalAdminService.batchPublish(List.of(festival.getId()));

        // then
        assertThat(response.publishedIds()).containsExactly(festival.getId());
        assertThat(response.failed()).isEmpty();
    }

    @Test
    void 중복된_id는_한_번만_처리된다() {
        // given
        Festival festival = 발행_가능한_축제("세종연회");
        반영한다();

        // when
        FestivalBatchPublishResponse response = festivalAdminService.batchPublish(
                List.of(festival.getId(), festival.getId(), festival.getId())
        );

        // then
        assertThat(response.publishedIds()).containsExactly(festival.getId());
    }

    @Test
    void 응답은_요청한_순서를_유지한다() {
        // given
        Festival 먼저_넣은_축제 = 발행_가능한_축제("먼저");
        Festival 나중에_넣은_축제 = 발행_가능한_축제("나중에");
        반영한다();

        // when - id 오름차순의 반대로 요청한다
        FestivalBatchPublishResponse response = festivalAdminService.batchPublish(
                List.of(나중에_넣은_축제.getId(), 먼저_넣은_축제.getId())
        );

        // then
        assertThat(response.publishedIds())
                .containsExactly(나중에_넣은_축제.getId(), 먼저_넣은_축제.getId());
    }

    @Test
    void 빈_목록이면_FESTIVAL_INVALID_IDS로_막힌다() {
        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> festivalAdminService.batchPublish(List.of())
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_IDS);
    }

    @Test
    void 백_개를_넘으면_FESTIVAL_INVALID_IDS로_막힌다() {
        // given
        List<Long> 백한_개 = LongStream.rangeClosed(1, 101).boxed().toList();

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> festivalAdminService.batchPublish(백한_개)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_IDS);
    }

    @Test
    void null이_섞여_있으면_FESTIVAL_INVALID_IDS로_막힌다() {
        // given - Arrays.asList는 null 원소를 허용한다
        List<Long> 가변_목록 = Arrays.asList(1L, null, 3L);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> festivalAdminService.batchPublish(가변_목록)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(FestivalErrorCode.FESTIVAL_INVALID_IDS);
    }

    private Festival 발행_가능한_축제(String 이름) {
        Festival festival = 축제를_넣는다(이름, 주최, 위도, 경도);
        라인업에_올린다(festival);
        return festival;
    }

    private Festival 축제를_넣는다(String 이름, Host host, Double latitude, Double longitude) {
        Festival festival = Festival.builder()
                .host(host)
                .name(이름)
                .startDate(LocalDate.of(2026, 9, 10))
                .endDate(LocalDate.of(2026, 9, 12))
                .latitude(latitude)
                .longitude(longitude)
                .build();
        em.persist(festival);
        em.flush();
        return festival;
    }

    private void 라인업에_올린다(Festival festival) {
        em.persist(Lineup.builder()
                .festival(festival)
                .artist(아티스트)
                .day(1)
                .displayOrder(다음_출연_순서++)
                .build());
    }

    private Artist 아티스트를_넣는다() {
        Artist artist = Artist.builder()
                .name("잔나비")
                .genre(ArtistGenre.BAND)
                .needsReview(false)
                .build();
        em.persist(artist);
        return artist;
    }

    private Instant 발행시각(Festival festival) {
        em.flush();
        return festivalRepository.findById(festival.getId()).orElseThrow().getPublishedAt();
    }

    private void 반영한다() {
        em.flush();
        em.clear();
    }
}
