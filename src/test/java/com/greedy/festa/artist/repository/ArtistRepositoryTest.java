package com.greedy.festa.artist.repository;

import com.greedy.festa.artist.dto.ArtistSortType;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.support.PostgresTestSupport;
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

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ArtistAliasRepository artistAliasRepository;

    @Autowired
    private EntityManager em;

    private Festival 축제;
    private int 다음_출연_순서 = 1;

    @BeforeEach
    void setUp() {
        Host 주최 = em.merge(Host.builder().name("세종대학교").region("서울 광진구").build());
        축제 = Festival.builder()
                .host(주최)
                .name("대동제")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 3))
                .build();
        em.persist(축제);
    }

    @Test
    void 장르를_주지_않으면_전체가_나온다() {
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        아티스트를_넣는다("다이나믹 듀오", ArtistGenre.HIPHOP, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = 조회한다(null, null, null, ArtistSortType.NAME);

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
                조회한다(null, ArtistGenre.BAND, null, ArtistSortType.NAME);

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

        Page<ArtistWithAppearanceCount> 결과 = 조회한다(null, null, null, ArtistSortType.NAME);

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
                조회한다(null, null, null, ArtistSortType.APPEARANCES);

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

        Page<ArtistWithAppearanceCount> 결과 = 조회한다("다듀", null, null, ArtistSortType.NAME);

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

        Page<ArtistWithAppearanceCount> 결과 = 조회한다("다듀", null, null, ArtistSortType.NAME);

        assertThat(결과.getContent()).hasSize(1);
    }

    @Test
    void 검토_대기만_거를_수_있다() {
        아티스트를_넣는다("잔나비 (밴드)", null, true);
        아티스트를_넣는다("잔나비", ArtistGenre.BAND, false);
        반영한다();

        Page<ArtistWithAppearanceCount> 결과 = 조회한다(null, null, true, ArtistSortType.NAME);

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
                조회한다(null, ArtistGenre.BAND, null, ArtistSortType.NAME);

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

    private Page<ArtistWithAppearanceCount> 조회한다(
            String q, ArtistGenre genre, Boolean needsReview, ArtistSortType sort
    ) {
        return artistRepository.findAllWithAppearanceCount(
                needsReview, genre, q, PageRequest.of(0, 10, sort.toSort()));
    }

    private Artist 아티스트를_넣는다(String 이름, ArtistGenre 장르, boolean 검토대기) {
        Artist 아티스트 = Artist.builder()
                .name(이름)
                .genre(장르)
                .needsReview(검토대기)
                .build();
        em.persist(아티스트);
        return 아티스트;
    }

    private void 별칭을_넣는다(Artist 아티스트, String 이름) {
        em.persist(ArtistAlias.builder().artist(아티스트).name(이름).build());
    }

    private void 라인업에_올린다(Artist 아티스트) {
        em.persist(Lineup.builder()
                .festival(축제)
                .artist(아티스트)
                .day(1)
                .displayOrder(다음_출연_순서++)
                .build());
    }

    private void 반영한다() {
        em.flush();
        em.clear();
    }
}
