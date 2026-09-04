package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistMatchReason;
import com.greedy.festa.artist.dto.ArtistMergeCandidateResponse;
import com.greedy.festa.artist.dto.ArtistMergeCandidateResponse.ArtistCandidate;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistAppearanceCount;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.support.fixture.ArtistFixture;
import com.greedy.festa.support.fixture.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.BDDMockito.given;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
public class ArtistMergeCandidateServiceTest {

    @Mock
    ArtistRepository artistRepository;

    @Mock
    ArtistAliasRepository artistAliasRepository;

    @InjectMocks
    ArtistMergeCandidateService artistMergeCandidateService;

    private static final Long 기준_id = 1L;
    private static final Long 후보_id = 2L;
    private static final Long 기본_limit = 5L;

    @ParameterizedTest
    @ValueSource(longs = {0L, 21L})
    void limit이_1과_20_사이가_아니면_실패한다(Long limit) {
        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistMergeCandidateService.findAll(기준_id, limit)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_LIMIT);
    }

    @Test
    void 없는_아티스트를_조회하면_실패한다() {
        // given
        given(artistRepository.findById(기준_id)).willReturn(Optional.empty());

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistMergeCandidateService.findAll(기준_id, 기본_limit)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    void 공백과_대소문자만_다른_이름은_완전_일치로_잡는다() {
        // given
        Artist 기준 = 아티스트(기준_id, "DAY 6");
        Artist 후보 = 아티스트(후보_id, "Day6");
        조회를_준비한다(기준, List.of(후보), List.of(), List.of(), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertSoftly(softly -> {
            softly.assertThat(candidates).hasSize(1);
            softly.assertThat(candidates.getFirst().artistId()).isEqualTo(후보_id);
            softly.assertThat(candidates.getFirst().similarity()).isEqualTo(0.9);
            softly.assertThat(candidates.getFirst().reasons())
                    .containsExactly(ArtistMatchReason.NAME_SIMILAR);
        });
    }

    @Test
    void 한쪽_이름이_다른_쪽에_포함되면_부분_일치로_잡는다() {
        // given
        Artist 기준 = 아티스트(기준_id, "잔나비 (밴드)");
        Artist 후보 = 아티스트(후보_id, "잔나비");
        조회를_준비한다(기준, List.of(후보), List.of(), List.of(), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertSoftly(softly -> {
            softly.assertThat(candidates).hasSize(1);
            softly.assertThat(candidates.getFirst().similarity()).isEqualTo(0.5);
            softly.assertThat(candidates.getFirst().reasons())
                    .containsExactly(ArtistMatchReason.NAME_SIMILAR);
        });
    }

    @Test
    void 별칭끼리_겹치면_완전_일치로_잡는다() {
        // given
        Artist 기준 = 아티스트(기준_id, "잔나비");
        Artist 후보 = 아티스트(후보_id, "Jannabi Band");
        조회를_준비한다(기준, List.of(후보),
                List.of(별칭(기준, "JANNABI"), 별칭(후보, "Jannabi")),
                List.of(), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertSoftly(softly -> {
            softly.assertThat(candidates).hasSize(1);
            softly.assertThat(candidates.getFirst().similarity()).isEqualTo(0.9);
            softly.assertThat(candidates.getFirst().reasons())
                    .containsExactly(ArtistMatchReason.ALIAS_MATCH);
        });
    }

    @Test
    void 후보의_이름이_기준의_별칭과_같으면_잡는다() {
        // given 후보에는 별칭이 없다. 검사가 한쪽 방향만 보면 놓친다
        Artist 기준 = 아티스트(기준_id, "잔나비");
        Artist 후보 = 아티스트(후보_id, "Jannabi");
        조회를_준비한다(기준, List.of(후보), List.of(별칭(기준, "JANNABI")), List.of(), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertSoftly(softly -> {
            softly.assertThat(candidates).hasSize(1);
            softly.assertThat(candidates.getFirst().reasons())
                    .containsExactly(ArtistMatchReason.ALIAS_MATCH);
        });
    }

    @Test
    void 같은_축제에만_함께_선_아티스트는_후보가_아니다() {
        // given 이름도 별칭도 전혀 안 겹친다. 같은 축제 출연만 있다
        Artist 기준 = 아티스트(기준_id, "잔나비");
        Artist 무관한_아티스트 = 아티스트(후보_id, "십센치");
        조회를_준비한다(기준, List.of(무관한_아티스트), List.of(), List.of(), List.of(후보_id));

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    void 같은_축제_출연은_이미_걸린_후보의_점수를_올린다() {
        // given
        Artist 기준 = 아티스트(기준_id, "DAY 6");
        Artist 후보 = 아티스트(후보_id, "Day6");
        조회를_준비한다(기준, List.of(후보), List.of(), List.of(), List.of(후보_id));

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertSoftly(softly -> {
            softly.assertThat(candidates.getFirst().similarity()).isEqualTo(1.0);
            softly.assertThat(candidates.getFirst().reasons())
                    .containsExactly(ArtistMatchReason.NAME_SIMILAR, ArtistMatchReason.SAME_FESTIVAL);
        });
    }

    @Test
    void 세_글자_미만인_이름은_부분_일치로_잡지_않는다() {
        // given "iu"가 "radius" 안에 들어 있지만 같은 아티스트가 아니다
        Artist 기준 = 아티스트(기준_id, "IU");
        Artist 무관한_아티스트 = 아티스트(후보_id, "Radius");
        조회를_준비한다(기준, List.of(무관한_아티스트), List.of(), List.of(), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    void 이름이_기호뿐이면_아무와도_매칭되지_않는다() {
        // given 정규화하면 빈 문자열이 된다. 빈 문자열은 모든 문자열에 포함된다
        Artist 기준 = 아티스트(기준_id, "잔나비");
        Artist 기호_이름 = 아티스트(후보_id, "???");
        조회를_준비한다(기준, List.of(기호_이름), List.of(), List.of(), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    void 점수가_높은_순으로_정렬하고_limit만큼_자른다() {
        // given
        Artist 기준 = 아티스트(기준_id, "잔나비");
        Artist 부분_일치 = 아티스트(2L, "잔나비 밴드");
        Artist 완전_일치 = 아티스트(3L, "잔 나 비");
        조회를_준비한다(기준, List.of(부분_일치, 완전_일치), List.of(), List.of(), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 1L);

        // then
        assertSoftly(softly -> {
            softly.assertThat(candidates).hasSize(1);
            softly.assertThat(candidates.getFirst().artistId()).isEqualTo(3L);
            softly.assertThat(candidates.getFirst().similarity()).isEqualTo(0.9);
        });
    }

    @Test
    void 점수가_같으면_출연_횟수가_많은_쪽이_앞선다() {
        // given
        Artist 기준 = 아티스트(기준_id, "잔나비");
        Artist 적게_나온_아티스트 = 아티스트(2L, "잔나비");
        Artist 많이_나온_아티스트 = 아티스트(3L, "잔 나 비");
        조회를_준비한다(기준, List.of(적게_나온_아티스트, 많이_나온_아티스트), List.of(),
                List.of(출연(2L, 3L), 출연(3L, 12L)), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertSoftly(softly -> {
            softly.assertThat(candidates).extracting(ArtistCandidate::artistId)
                    .containsExactly(3L, 2L);
            softly.assertThat(candidates.getFirst().appearanceCount()).isEqualTo(12L);
        });
    }

    @Test
    void 점수와_출연_횟수가_같으면_id가_작은_쪽이_앞선다() {
        // given 조회 순서를 뒤집어 넣는다. tie-break가 없으면 조회 순서가 그대로 결과 순서가 된다
        Artist 기준 = 아티스트(기준_id, "잔나비");
        Artist 작은_id = 아티스트(2L, "잔나비");
        Artist 큰_id = 아티스트(3L, "잔 나 비");
        조회를_준비한다(기준, List.of(큰_id, 작은_id), List.of(),
                List.of(출연(2L, 5L), 출연(3L, 5L)), List.of());

        // when
        List<ArtistCandidate> candidates = 후보들(기준, 기본_limit);

        // then
        assertThat(candidates).extracting(ArtistCandidate::artistId).containsExactly(2L, 3L);
    }

    @Test
    void 겹치는_아티스트가_없으면_빈_목록을_준다() {
        // given
        Artist 기준 = 아티스트(기준_id, "잔나비");
        Artist 무관한_아티스트 = 아티스트(후보_id, "십센치");
        조회를_준비한다(기준, List.of(무관한_아티스트), List.of(), List.of(), List.of());

        // when
        ArtistMergeCandidateResponse response = artistMergeCandidateService.findAll(기준_id, 기본_limit);

        // then
        assertSoftly(softly -> {
            softly.assertThat(response.candidates()).isEmpty();
            softly.assertThat(response.source().artistId()).isEqualTo(기준_id);
            softly.assertThat(response.source().name()).isEqualTo("잔나비");
        });
    }

    private List<ArtistCandidate> 후보들(Artist 기준, Long limit) {
        return artistMergeCandidateService.findAll(기준.getId(), limit).candidates();
    }

    private void 조회를_준비한다(Artist 기준, List<Artist> 나머지, List<ArtistAlias> 별칭들,
                          List<ArtistAppearanceCount> 출연들, List<Long> 같은_축제_id들) {
        given(artistRepository.findById(기준.getId())).willReturn(Optional.of(기준));
        given(artistRepository.findAllByIdNot(기준.getId())).willReturn(나머지);
        given(artistAliasRepository.findAll()).willReturn(별칭들);
        given(artistRepository.countAllAppearances()).willReturn(출연들);
        given(artistRepository.findSameFestivalArtistIds(기준.getId())).willReturn(같은_축제_id들);
    }

    private Artist 아티스트(Long id, String name) {
        return Fixtures.withId(
                ArtistFixture.artist(name).needsReview(false).build(), id);
    }

    private ArtistAlias 별칭(Artist artist, String name) {
        return ArtistFixture.alias(artist, name).build();
    }

    private ArtistAppearanceCount 출연(Long artistId, Long appearanceCount) {
        return new 출연_횟수(artistId, appearanceCount);
    }

    private record 출연_횟수(Long artistId, Long appearanceCount) implements ArtistAppearanceCount {

        @Override
        public Long getArtistId() {
            return artistId;
        }

        @Override
        public Long getAppearanceCount() {
            return appearanceCount;
        }
    }
}
