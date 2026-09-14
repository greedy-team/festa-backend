package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.dto.ArtistAdminSortType;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.lineup.repository.LineupRepository;
import com.greedy.festa.support.PostgresTestSupport;
import com.greedy.festa.support.fixture.ArtistFixture;
import com.greedy.festa.support.fixture.FestivalFixture;
import com.greedy.festa.support.fixture.HostFixture;
import com.greedy.festa.support.fixture.LineupFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class ArtistRepositoryTest extends PostgresTestSupport {

    @Test
    void adminLikeWildcardsAndEscapeCharacterAreLiteralInPostgres() {
        em.persist(ArtistFixture.artist("Discount 50% Artist").genre(ArtistGenre.DANCE).build());
        em.persist(ArtistFixture.artist("sub_artist").genre(ArtistGenre.DANCE).build());
        em.persist(ArtistFixture.artist("path\\artist").genre(ArtistGenre.DANCE).build());
        em.persist(ArtistFixture.artist("ordinary artist").genre(ArtistGenre.DANCE).build());
        em.flush();
        em.clear();

        Page<ArtistWithAppearanceCount> percent = artistRepository.findAllWithAppearanceCount(
                null, null, "\\%", PageRequest.of(0, 10, ArtistAdminSortType.NAME.toSort()));
        Page<ArtistWithAppearanceCount> underscore = artistRepository.findAllWithAppearanceCount(
                null, null, "\\_", PageRequest.of(0, 10, ArtistAdminSortType.NAME.toSort()));
        Page<ArtistWithAppearanceCount> backslash = artistRepository.findAllWithAppearanceCount(
                null, null, "\\\\", PageRequest.of(0, 10, ArtistAdminSortType.NAME.toSort()));

        assertThat(percent.getContent()).extracting(row -> row.getArtist().getName())
                .containsExactly("Discount 50% Artist");
        assertThat(percent.getTotalElements()).isEqualTo(1);
        assertThat(underscore.getContent()).extracting(row -> row.getArtist().getName())
                .containsExactly("sub_artist");
        assertThat(underscore.getTotalElements()).isEqualTo(1);
        assertThat(backslash.getContent()).extracting(row -> row.getArtist().getName())
                .containsExactly("path\\artist");
        assertThat(backslash.getTotalElements()).isEqualTo(1);
    }

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ArtistAliasRepository artistAliasRepository;

    @Autowired
    private LineupRepository lineupRepository;

    @Autowired
    private EntityManager em;

    private Host 주최;
    private Festival 축제;
    private int 다음_출연_순서 = 1;

    @BeforeEach
    void setUp() {
        주최 = em.merge(HostFixture.host("테스트대학교").region("서울 광진구").build());
        // 출연 횟수는 발행되고 이미 끝난 축제만 센다 (DEC-0053).
        축제 = 축제를_넣는다("대동제", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), true);
    }

    @Test
    void 장르를_주지_않으면_전체가_나온다() {
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = 조회한다(null, null, null, ArtistAdminSortType.NAME);

        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getName())
                .containsExactly("다이나믹 듀오", "잔나비");
    }

    @Test
    void 장르로_거를_수_있다() {
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 =
                조회한다(null, ArtistGenre.BAND, null, ArtistAdminSortType.NAME);

        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getName())
                .containsExactly("잔나비");
    }

    @Test
    void 라인업이_없는_아티스트도_출연_0으로_나온다() {
        Artist 잔나비 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        라인업에_올린다(잔나비);
        아티스트를_넣는다("아이유", ArtistGenre.DANCE, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = 조회한다(null, null, null, ArtistAdminSortType.NAME);

        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getName(), ArtistWithAppearanceCount::getAppearanceCount)
                .containsExactly(tuple("아이유", 0L), tuple("잔나비", 1L));
    }

    @Test
    void 출연_횟수로_정렬한다() {
        Artist 잔나비 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        라인업에_올린다(잔나비);
        라인업에_올린다(잔나비);
        라인업에_올린다(잔나비);

        Artist 다듀 = 아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        라인업에_올린다(다듀);

        아티스트를_넣는다("아이유", ArtistGenre.DANCE, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 =
                조회한다(null, null, null, ArtistAdminSortType.APPEARANCES);

        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getName())
                .containsExactly("잔나비", "다이나믹 듀오", "아이유");
    }

    @Test
    void 검색어가_별칭에도_걸린다() {
        Artist 다듀 = 아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        별칭을_넣는다(다듀, "다듀");
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = 조회한다("다듀", null, null, ArtistAdminSortType.NAME);

        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getName())
                .containsExactly("다이나믹 듀오");
    }

    @Test
    void 별칭이_여러_개_걸려도_아티스트가_중복으로_나오지_않는다() {
        Artist 다듀 = 아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        별칭을_넣는다(다듀, "다듀");
        별칭을_넣는다(다듀, "다듀 형님들");
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = 조회한다("다듀", null, null, ArtistAdminSortType.NAME);

        assertThat(결과.getContent()).hasSize(1);
    }

    @Test
    void 검토_대기만_거를_수_있다() {
        아티스트를_넣는다("잔나비 (밴드)", null, true);
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = 조회한다(null, null, true, ArtistAdminSortType.NAME);

        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getName())
                .containsExactly("잔나비 (밴드)");
    }

    @Test
    void 필터를_적용하면_전체_건수도_함께_줄어든다() {
        // countQuery를 따로 줬으므로 본 쿼리와 같은 집합을 세는지 확인해야 한다.
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        아티스트를_넣는다("아이유", ArtistGenre.DANCE, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 =
                조회한다(null, ArtistGenre.BAND, null, ArtistAdminSortType.NAME);

        assertThat(결과.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 출연_횟수를_단건으로_센다() {
        Artist 잔나비 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        라인업에_올린다(잔나비);
        라인업에_올린다(잔나비);
        반영한다();

        assertThat(artistRepository.countAppearancesByArtistId(잔나비.getId())).isEqualTo(2);
    }

    @Test
    void 미발행_축제와_끝나지_않은_축제의_출연은_세지_않는다() {
        // given
        Artist 잔나비 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        라인업에_올린다(잔나비);
        라인업에_올린다(잔나비, 축제를_넣는다("미발행 축제", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), false));
        라인업에_올린다(잔나비, 축제를_넣는다("다가올 축제", LocalDate.of(2099, 5, 1), LocalDate.of(2099, 5, 3), true));
        반영한다();

        // when
        Page<ArtistWithAppearanceCount> 결과 = 조회한다(null, null, null, ArtistAdminSortType.NAME);

        // then
        assertThat(결과.getContent())
                .extracting(ArtistWithAppearanceCount::getAppearanceCount)
                .containsExactly(1L);
        assertThat(artistRepository.countAppearancesByArtistId(잔나비.getId())).isEqualTo(1);
    }

    @Test
    void 삭제_판정은_발행_여부와_무관하게_모든_라인업을_센다() {
        // 삭제 가드는 FK 참조 유무를 묻는 것이라 미발행 축제의 출연도 막아야 한다.
        // given
        Artist 잔나비 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        라인업에_올린다(잔나비, 축제를_넣는다("미발행 축제", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), false));
        반영한다();

        // then
        assertThat(artistRepository.countAppearancesByArtistId(잔나비.getId())).isZero();
        assertThat(artistRepository.countLineupsByArtistId(잔나비.getId())).isEqualTo(1);
    }

    @Test
    void 이름_묶음으로_중복을_확인한다() {
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        반영한다();

        assertThat(artistRepository.existsByNameIn(List.of("아이유", "잔나비"))).isTrue();
        assertThat(artistRepository.existsByNameIn(List.of("아이유", "다듀"))).isFalse();
    }

    @Test
    void 아티스트_id로_별칭을_한번에_가져온다() {
        Artist 다듀 = 아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        별칭을_넣는다(다듀, "다듀");
        Artist 잔나비 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        별칭을_넣는다(잔나비, "JANNABI");
        반영한다();

        List<ArtistAlias> 별칭들 =
                artistAliasRepository.findByArtistIdIn(List.of(다듀.getId(), 잔나비.getId()));

        assertThat(별칭들)
                .extracting(ArtistAlias::getName)
                .containsExactlyInAnyOrder("다듀", "JANNABI");
    }

    @Test
    void 공개_목록은_별칭_검색을_중복없이_페이지_카운트에_반영한다() {
        Artist 다듀 = 아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        별칭을_넣는다(다듀, "다듀");
        별칭을_넣는다(다듀, "다이나믹");
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = artistRepository.findPublicByAppearances(
                null, "다", LocalDate.of(2026, 6, 1), PageRequest.of(0, 10));

        assertThat(결과.getTotalElements()).isEqualTo(1);
        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getName())
                .containsExactly("다이나믹 듀오");
    }

    @Test
    void 공개_목록은_발행된_종료_축제만_집계하고_동점은_id_오름차순이다() {
        Artist 첫째 = 아티스트를_넣는다("첫째", ArtistGenre.BAND, false);
        Artist 둘째 = 아티스트를_넣는다("둘째", ArtistGenre.BAND, false);
        라인업에_올린다(첫째);
        Festival 미발행 = 축제를_넣는다("미발행", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), false);
        Festival 진행중 = 축제를_넣는다("진행중", LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 2), true);
        라인업에_올린다(둘째, 미발행);
        라인업에_올린다(둘째, 진행중);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = artistRepository.findPublicByAppearances(
                ArtistGenre.BAND, null, LocalDate.of(2026, 6, 1), PageRequest.of(0, 10));

        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getId(), ArtistWithAppearanceCount::getAppearanceCount)
                .containsExactly(tuple(첫째.getId(), 1L), tuple(둘째.getId(), 0L));
    }

    @Test
    void 공개_목록의_출연_횟수는_같은_축제의_여러_라인업을_한번만_센다() {
        Artist 아티스트 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        Festival 같은축제 = 축제를_넣는다(
                "이틀 출연 축제", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), true);
        라인업에_올린다(아티스트, 같은축제, 1);
        라인업에_올린다(아티스트, 같은축제, 2);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = artistRepository.findPublicByAppearances(
                null, null, LocalDate.of(2026, 6, 1), PageRequest.of(0, 10));

        assertThat(결과.getContent().getFirst().getAppearanceCount()).isEqualTo(1L);
    }

    @Test
    void 공개_목록_검색은_LIKE_와일드카드를_문자_그대로_찾는다() {
        아티스트를_넣는다("100% 라이브", ArtistGenre.BAND, false);
        아티스트를_넣는다("100점 라이브", ArtistGenre.BAND, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = artistRepository.findPublicByName(
                null, "100\\%", LocalDate.of(2026, 6, 1), PageRequest.of(0, 10));

        assertThat(결과.getContent())
                .extracting(row -> row.getArtist().getName())
                .containsExactly("100% 라이브");
    }

    @Test
    void 공개_이름순은_페이지네이션_전에_적용되고_total도_전체_필터_결과다() {
        Artist 가나다 = 아티스트를_넣는다("가나다", ArtistGenre.BAND, false);
        Artist 라마바 = 아티스트를_넣는다("라마바", ArtistGenre.BAND, false);
        아티스트를_넣는다("힙합", ArtistGenre.HIPHOP, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 첫_페이지 = artistRepository.findPublicByName(
                ArtistGenre.BAND, null, LocalDate.of(2026, 6, 1), PageRequest.of(0, 1));
        Page<ArtistWithAppearanceCount> 둘째_페이지 = artistRepository.findPublicByName(
                ArtistGenre.BAND, null, LocalDate.of(2026, 6, 1), PageRequest.of(1, 1));

        assertThat(첫_페이지.getTotalElements()).isEqualTo(2);
        assertThat(첫_페이지.getTotalPages()).isEqualTo(2);
        assertThat(첫_페이지.getContent().getFirst().getArtist().getId()).isEqualTo(가나다.getId());
        assertThat(둘째_페이지.getContent().getFirst().getArtist().getId()).isEqualTo(라마바.getId());
    }

    @Test
    void 최근_축제_조회는_아티스트별_최신_발행_종료_축제를_먼저_준다() {
        Artist 아티스트 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        Festival 예전 = 축제를_넣는다("예전 축제", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), true);
        Festival 최근 = 축제를_넣는다("최근 축제", LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 11), true);
        라인업에_올린다(아티스트, 예전);
        라인업에_올린다(아티스트, 최근);
        반영한다();

        List<ArtistRecentFestivalRow> 결과 = artistRepository.findRecentFestivals(
                List.of(아티스트.getId()), LocalDate.of(2026, 6, 1));

        assertThat(결과)
                .extracting(ArtistRecentFestivalRow::getFestivalName)
                .containsExactly("최근 축제", "예전 축제");
    }

    @Test
    void 최근_축제는_상세_이력과_같이_시작일_내림차순으로_정한다() {
        Artist 아티스트 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        Festival 먼저시작_나중종료 = 축제를_넣는다(
                "장기 축제", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20), true);
        Festival 나중시작_먼저종료 = 축제를_넣는다(
                "단기 축제", LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), true);
        라인업에_올린다(아티스트, 먼저시작_나중종료);
        라인업에_올린다(아티스트, 나중시작_먼저종료);
        반영한다();

        List<ArtistRecentFestivalRow> 결과 = artistRepository.findRecentFestivals(
                List.of(아티스트.getId()), LocalDate.of(2026, 6, 1));

        assertThat(결과)
                .extracting(ArtistRecentFestivalRow::getFestivalName)
                .containsExactly("단기 축제", "장기 축제");
    }

    @Test
    void 상세_라인업은_발행된_축제만_Festival과_Host를_함께_가져온다() {
        Artist 아티스트 = 아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        Festival 발행 = 축제를_넣는다("발행 축제", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), true);
        Festival 미발행 = 축제를_넣는다("미발행 축제", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), false);
        라인업에_올린다(아티스트, 발행);
        라인업에_올린다(아티스트, 미발행);
        반영한다();

        List<Lineup> 결과 = lineupRepository.findPublishedByArtistId(아티스트.getId());

        assertThat(결과).hasSize(1);
        assertThat(결과.getFirst().getFestival().getName()).isEqualTo("발행 축제");
        assertThat(결과.getFirst().getFestival().getHost().getName()).isEqualTo("테스트대학교");
    }

    private Page<ArtistWithAppearanceCount> 조회한다(
            String q, ArtistGenre genre, Boolean needsReview, ArtistAdminSortType sort
    ) {
        return artistRepository.findAllWithAppearanceCount(
                needsReview, genre, q, PageRequest.of(0, 10, sort.toSort()));
    }

    private Artist 아티스트를_넣는다(String 이름, ArtistGenre 장르, boolean 검토대기) {
        Artist 아티스트 = ArtistFixture.artist(이름)
                .genre(장르)
                .needsReview(검토대기)
                .build();
        em.persist(아티스트);
        return 아티스트;
    }

    private void 별칭을_넣는다(Artist 아티스트, String 이름) {
        em.persist(ArtistFixture.alias(아티스트, 이름).build());
    }

    // publishedAt은 빌더에 없어 네이티브 쿼리로 넣는다.
    private Festival 축제를_넣는다(String 이름, LocalDate 시작, LocalDate 종료, boolean 발행됨) {
        Festival festival = FestivalFixture.festival(이름)
                .host(주최)
                .startDate(시작)
                .endDate(종료)
                .build();
        em.persist(festival);
        em.flush();
        if (발행됨) {
            em.createNativeQuery("UPDATE festival SET published_at = :at WHERE id = :id")
                    .setParameter("at", Instant.parse("2026-05-04T00:00:00Z"))
                    .setParameter("id", festival.getId())
                    .executeUpdate();
        }
        return festival;
    }

    private void 라인업에_올린다(Artist 아티스트) {
        라인업에_올린다(아티스트, 축제);
    }

    private void 라인업에_올린다(Artist 아티스트, Festival 대상축제) {
        라인업에_올린다(아티스트, 대상축제, 1);
    }

    private void 라인업에_올린다(Artist 아티스트, Festival 대상축제, int 일차) {
        em.persist(LineupFixture.lineup(대상축제, 아티스트)
                .day(일차)
                .displayOrder(다음_출연_순서++)
                .build());
    }

    private void 반영한다() {
        em.flush();
        em.clear();
    }
}
