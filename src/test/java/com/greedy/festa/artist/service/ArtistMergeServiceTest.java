package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistMergeRequest;
import com.greedy.festa.artist.dto.ArtistMergeResponse;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.config.JpaConfig;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.lineup.entity.Lineup;
import com.greedy.festa.lineup.repository.LineupRepository;
import com.greedy.festa.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaConfig.class, ArtistMergeService.class})
class ArtistMergeServiceTest extends PostgresTestSupport {

    @Autowired
    private ArtistMergeService artistMergeService;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ArtistAliasRepository artistAliasRepository;

    @Autowired
    private LineupRepository lineupRepository;

    @Autowired
    private EntityManager em;

    private Festival 대동제;
    private Festival 해변가요제;

    @BeforeEach
    void setUp() {
        Host 주최 = em.merge(Host.builder().name("테스트대학교").region("서울 광진구").build());
        대동제 = 축제를_넣는다(주최, "대동제");
        해변가요제 = 축제를_넣는다(주최, "해변가요제");
    }

    @Test
    void 흡수된_아티스트의_출연_이력이_남을_아티스트로_옮겨진다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        라인업에_올린다(대동제, IU, 1, 1);
        라인업에_올린다(해변가요제, IU, 1, 1);
        반영한다();

        ArtistMergeResponse 결과 = 병합한다(아이유, true, IU);
        반영한다();

        assertSoftly(softly -> {
            softly.assertThat(결과.movedAppearances()).isEqualTo(2);
            softly.assertThat(결과.removedDuplicates()).isZero();
            softly.assertThat(lineupRepository.findAll())
                    .extracting(라인업 -> 라인업.getArtist().getId())
                    .containsOnly(아이유.getId());
        });
    }

    @Test
    void 같은_축제_같은_일차에_겹치면_순서가_앞선_라인업만_남는다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        라인업에_올린다(대동제, 아이유, 1, 5);
        라인업에_올린다(대동제, IU, 1, 2);
        반영한다();

        ArtistMergeResponse 결과 = 병합한다(아이유, true, IU);
        반영한다();

        assertSoftly(softly -> {
            softly.assertThat(결과.removedDuplicates()).isEqualTo(1);
            softly.assertThat(lineupRepository.findAll())
                    .extracting(Lineup::getDisplayOrder)
                    .containsExactly(2);
        });
    }

    @Test
    void 흡수_대상끼리_겹쳐도_순서가_앞선_하나만_남는다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        Artist 아이유_IU = 아티스트를_넣는다("아이유(IU)");
        라인업에_올린다(대동제, IU, 1, 2);
        라인업에_올린다(대동제, 아이유_IU, 1, 7);
        반영한다();

        ArtistMergeResponse 결과 = 병합한다(아이유, true, IU, 아이유_IU);
        반영한다();

        assertSoftly(softly -> {
            softly.assertThat(결과.movedAppearances()).isEqualTo(2);
            softly.assertThat(결과.removedDuplicates()).isEqualTo(1);
            softly.assertThat(lineupRepository.findAll())
                    .extracting(Lineup::getDisplayOrder)
                    .containsExactly(2);
        });
    }

    @Test
    void 다른_일차의_출연은_중복이_아니다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        라인업에_올린다(대동제, 아이유, 1, 1);
        라인업에_올린다(대동제, IU, 2, 1);
        반영한다();

        ArtistMergeResponse 결과 = 병합한다(아이유, true, IU);
        반영한다();

        assertSoftly(softly -> {
            softly.assertThat(결과.removedDuplicates()).isZero();
            softly.assertThat(lineupRepository.findAll()).hasSize(2);
        });
    }

    @Test
    void 별칭을_보존하면_흡수된_이름과_별칭이_모두_옮겨진다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        별칭을_넣는다(IU, "아이유(IU)");
        반영한다();

        ArtistMergeResponse 결과 = 병합한다(아이유, true, IU);
        반영한다();

        assertThat(결과.otherNames()).containsExactlyInAnyOrder("IU", "아이유(IU)");
    }

    @Test
    void 이미_가진_별칭과_겹치면_건너뛴다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        별칭을_넣는다(아이유, "IU");
        Artist IU = 아티스트를_넣는다("IU");
        반영한다();

        ArtistMergeResponse 결과 = 병합한다(아이유, true, IU);
        반영한다();

        assertSoftly(softly -> {
            softly.assertThat(결과.otherNames()).containsExactly("IU");
            softly.assertThat(artistAliasRepository.findAll()).hasSize(1);
        });
    }

    @Test
    void 별칭_보존_여부를_주지_않으면_보존한다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        별칭을_넣는다(IU, "아이유(IU)");
        반영한다();

        ArtistMergeResponse 결과 = artistMergeService.merge(
                new ArtistMergeRequest(아이유.getId(), List.of(IU.getId()), null));
        반영한다();

        assertThat(결과.otherNames()).containsExactlyInAnyOrder("IU", "아이유(IU)");
    }

    @Test
    void 별칭을_보존하지_않으면_흡수된_별칭이_사라진다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        별칭을_넣는다(IU, "아이유(IU)");
        반영한다();

        ArtistMergeResponse 결과 = 병합한다(아이유, false, IU);
        반영한다();

        assertSoftly(softly -> {
            softly.assertThat(결과.otherNames()).isEmpty();
            softly.assertThat(artistAliasRepository.findAll()).isEmpty();
        });
    }

    @Test
    void 출연_이력이_있는_아티스트도_병합으로_지워진다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        라인업에_올린다(대동제, IU, 1, 1);
        반영한다();

        병합한다(아이유, true, IU);
        반영한다();

        assertThat(artistRepository.findById(IU.getId())).isEmpty();
    }

    @Test
    void 병합_후_남은_아티스트는_검토_대기로_표시된다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        라인업에_올린다(대동제, IU, 1, 1);
        반영한다();

        ArtistMergeResponse 결과 = 병합한다(아이유, true, IU);
        반영한다();

        assertSoftly(softly -> {
            softly.assertThat(결과.needsReview()).isTrue();
            softly.assertThat(artistRepository.findById(아이유.getId()))
                    .get()
                    .extracting(Artist::isNeedsReview)
                    .isEqualTo(true);
        });
    }

    @Test
    void 흡수_목록에_같은_id가_여러_번_와도_하나로_센다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        반영한다();

        ArtistMergeResponse 결과 = artistMergeService.merge(new ArtistMergeRequest(
                아이유.getId(), List.of(IU.getId(), IU.getId(), IU.getId()), true
        ));
        반영한다();

        assertSoftly(softly -> {
            softly.assertThat(결과.mergedCount()).isEqualTo(1);
            softly.assertThat(artistRepository.findAll()).hasSize(1);
        });
    }

    @Test
    void 남길_아티스트가_흡수_목록에_있으면_실패한다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        반영한다();

        FestaException 예외 = catchThrowableOfType(FestaException.class,
                () -> 병합한다(아이유, true, IU, 아이유));

        assertThat(예외.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_SELF_MERGE);
    }

    @Test
    void 흡수_목록이_비어_있으면_실패한다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        반영한다();

        FestaException 예외 = catchThrowableOfType(FestaException.class,
                () -> artistMergeService.merge(new ArtistMergeRequest(아이유.getId(), List.of(), true)));

        assertThat(예외.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_SOURCE_IDS);
    }

    @Test
    void 흡수_목록이_없으면_실패한다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        반영한다();

        FestaException 예외 = catchThrowableOfType(FestaException.class,
                () -> artistMergeService.merge(new ArtistMergeRequest(아이유.getId(), null, true)));

        assertThat(예외.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_SOURCE_IDS);
    }

    @Test
    void 흡수_목록이_열_개를_넘으면_실패한다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        반영한다();

        List<Long> 열한_개 = LongStream.rangeClosed(1, 11).boxed().toList();

        FestaException 예외 = catchThrowableOfType(FestaException.class,
                () -> artistMergeService.merge(new ArtistMergeRequest(아이유.getId(), 열한_개, true)));

        assertThat(예외.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_SOURCE_IDS);
    }

    @Test
    void 없는_아티스트가_섞여_있으면_실패한다() {
        Artist 아이유 = 아티스트를_넣는다("아이유");
        Artist IU = 아티스트를_넣는다("IU");
        반영한다();

        FestaException 예외 = catchThrowableOfType(FestaException.class,
                () -> artistMergeService.merge(new ArtistMergeRequest(
                        아이유.getId(), List.of(IU.getId(), 999_999L), true
                )));

        assertThat(예외.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    private ArtistMergeResponse 병합한다(Artist 남길_아티스트, boolean 별칭_보존, Artist... 흡수될_아티스트) {
        List<Long> sourceIds = Arrays.stream(흡수될_아티스트)
                .map(Artist::getId)
                .toList();

        return artistMergeService.merge(
                new ArtistMergeRequest(남길_아티스트.getId(), sourceIds, 별칭_보존));
    }

    private Festival 축제를_넣는다(Host 주최, String 이름) {
        Festival 축제 = Festival.builder()
                .host(주최)
                .name(이름)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 3))
                .build();
        em.persist(축제);
        return 축제;
    }

    private Artist 아티스트를_넣는다(String 이름) {
        Artist 아티스트 = Artist.builder()
                .name(이름)
                .genre(ArtistGenre.BAND)
                .needsReview(false)
                .build();
        em.persist(아티스트);
        return 아티스트;
    }

    private void 별칭을_넣는다(Artist 아티스트, String 이름) {
        em.persist(ArtistAlias.builder().artist(아티스트).name(이름).build());
    }

    private void 라인업에_올린다(Festival 축제, Artist 아티스트, int 일차, int 순서) {
        em.persist(Lineup.builder()
                .festival(축제)
                .artist(아티스트)
                .day(일차)
                .displayOrder(순서)
                .build());
    }

    private void 반영한다() {
        em.flush();
        em.clear();
    }
}
