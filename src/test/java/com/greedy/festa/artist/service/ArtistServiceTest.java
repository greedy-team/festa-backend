package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistCreateRequest;
import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.ArtistUpdateRequest;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.global.exception.FestaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
public class ArtistServiceTest {

    @Mock
    ArtistRepository artistRepository;

    @Mock
    ArtistAliasRepository artistAliasRepository;

    @InjectMocks
    ArtistService artistService;

    private static final Long 아티스트_id = 1L;
    private static final String 아티스트_이름 = "BTS";
    private static final String 아티스트_인스타 = "https://instagram.com/bts";
    private static final List<String> 아티스트_별칭 = List.of("방탄소년단", "방탄");

    private static List<String> 잘못된_아티스트_이름() {
        return Arrays.asList(null, "", "   ", "아".repeat(101));
    }

    @ParameterizedTest
    @MethodSource("잘못된_아티스트_이름")
    void 아티스트_이름이_비었거나_100자를_넘으면_등록에_실패한다(String name) {
        // given
        ArtistCreateRequest request = 등록_요청(name, 아티스트_별칭);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_NAME);
        verify(artistRepository, never()).save(any());
    }

    @Test
    void 이미_있는_이름으로_등록하면_실패한다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, 아티스트_별칭);
        given(artistRepository.existsByName(아티스트_이름)).willReturn(true);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_DUPLICATE_NAME);
    }

    @Test
    void 별칭이_100자를_넘으면_등록에_실패한다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, List.of("아".repeat(101)));

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_ALIAS);
    }

    @Test
    void 별칭이_다른_아티스트의_이름과_겹치면_등록에_실패한다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, List.of("방탄소년단"));
        given(artistRepository.existsByName(아티스트_이름)).willReturn(false);
        given(artistRepository.existsByName("방탄소년단")).willReturn(true);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_DUPLICATE_ALIAS);
    }

    @Test
    void 별칭이_다른_아티스트의_별칭과_겹치면_등록에_실패한다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, List.of("방탄"));
        given(artistAliasRepository.existsByName(아티스트_이름)).willReturn(false);
        given(artistAliasRepository.existsByName("방탄")).willReturn(true);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_DUPLICATE_ALIAS);
    }

    @Test
    void 대표명이_다른_아티스트의_별칭과_겹치면_등록에_실패한다() {
        // given
        ArtistCreateRequest request = 등록_요청("JANNABI", null);
        given(artistAliasRepository.existsByName("JANNABI")).willReturn(true);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_DUPLICATE_NAME);
    }

    @Test
    void 대표명은_앞뒤_공백을_제거하고_중복을_검사한다() {
        // given
        ArtistCreateRequest request = 등록_요청("  " + 아티스트_이름 + "  ", null);
        given(artistRepository.existsByName(아티스트_이름)).willReturn(true);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.create(request)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_DUPLICATE_NAME);
    }

    @Test
    void 대표명은_앞뒤_공백을_제거하고_저장한다() {
        // given
        ArtistCreateRequest request = 등록_요청("  " + 아티스트_이름 + "  ", List.of(아티스트_이름, "방탄"));
        저장은_받은_엔티티를_그대로_돌려준다();

        // when
        ArtistResponse response = artistService.create(request);

        // then
        assertSoftly(softly -> {
            softly.assertThat(response.name()).isEqualTo(아티스트_이름);
            softly.assertThat(response.otherNames()).containsExactly("방탄");
        });
    }

    @Test
    void needsReview가_false로_아티스트가_등록된다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, 아티스트_별칭);
        저장은_받은_엔티티를_그대로_돌려준다();

        // when
        ArtistResponse response = artistService.create(request);

        // then
        ArgumentCaptor<Artist> captor = ArgumentCaptor.forClass(Artist.class);
        verify(artistRepository).save(captor.capture());

        assertSoftly(softly -> {
            softly.assertThat(captor.getValue().isNeedsReview()).isFalse();
            softly.assertThat(response.needsReview()).isFalse();
            softly.assertThat(response.name()).isEqualTo(아티스트_이름);
            softly.assertThat(response.otherNames()).isEqualTo(아티스트_별칭);
            softly.assertThat(response.genre()).isEqualTo(ArtistGenre.DANCE);
            softly.assertThat(response.appearanceCount()).isZero();
        });
    }

    @Test
    void 관리자_등록에서는_이미지가_비어_있다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, List.of());
        저장은_받은_엔티티를_그대로_돌려준다();

        // when
        ArtistResponse response = artistService.create(request);

        // then
        assertThat(response.imageUrl()).isNull();
    }

    @Test
    void 비었거나_공백인_별칭은_거부되지_않고_무시된다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, Arrays.asList(null, "", "   ", " 방탄 "));
        저장은_받은_엔티티를_그대로_돌려준다();

        // when
        ArtistResponse response = artistService.create(request);

        // then
        assertThat(response.otherNames()).containsExactly("방탄");
    }

    @Test
    void 대표명과_같은_별칭은_제외된다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, List.of(아티스트_이름, "방탄"));
        저장은_받은_엔티티를_그대로_돌려준다();

        // when
        ArtistResponse response = artistService.create(request);

        // then
        assertThat(response.otherNames()).containsExactly("방탄");
    }

    @Test
    void 요청_안에서_중복된_별칭은_하나만_남는다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, List.of("방탄", "방탄"));
        저장은_받은_엔티티를_그대로_돌려준다();

        // when
        ArtistResponse response = artistService.create(request);

        // then
        assertThat(response.otherNames()).containsExactly("방탄");
    }

    @Test
    void 별칭을_보내지_않아도_등록된다() {
        // given
        ArtistCreateRequest request = 등록_요청(아티스트_이름, null);
        저장은_받은_엔티티를_그대로_돌려준다();

        // when
        ArtistResponse response = artistService.create(request);

        // then
        assertSoftly(softly -> {
            softly.assertThat(response.name()).isEqualTo(아티스트_이름);
            softly.assertThat(response.otherNames()).isEmpty();
        });
    }

    @Test
    void 없는_아티스트는_수정할_수_없다() {
        // given
        given(artistRepository.findById(아티스트_id)).willReturn(Optional.empty());

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class,
                () -> artistService.update(아티스트_id, 수정_요청(null, null, null, null, null))
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    void 별칭을_보내지_않으면_별칭을_건드리지_않는다() {
        // given
        Artist artist = 수정할_아티스트가_있다();
        별칭이_있다(artist, "방탄");

        // when
        ArtistResponse response = artistService.update(아티스트_id, 수정_요청(null, null, null, null, null));

        // then
        assertThat(response.otherNames()).containsExactly("방탄");
        verify(artistAliasRepository, never()).deleteAll(any());
        verify(artistAliasRepository, never()).saveAll(any());
    }

    @Test
    void 빈_배열을_보내면_별칭이_전부_지워진다() {
        // given
        Artist artist = 수정할_아티스트가_있다();
        ArtistAlias 방탄 = 별칭이_있다(artist, "방탄");

        // when
        ArtistResponse response = artistService.update(아티스트_id, 수정_요청(null, List.of(), null, null, null));

        // then
        assertThat(response.otherNames()).isEmpty();
        verify(artistAliasRepository).deleteAll(List.of(방탄));
    }

    @Test
    void 같은_별칭을_다시_보내면_아무것도_저장하지_않는다() {
        // given
        Artist artist = 수정할_아티스트가_있다();
        별칭이_있다(artist, "방탄");

        // when
        artistService.update(아티스트_id, 수정_요청(null, List.of("방탄"), null, null, null));

        // then
        verify(artistAliasRepository).deleteAll(List.of());
        verify(artistAliasRepository).saveAll(List.of());
    }

    @Test
    void 보내지_않은_필드는_그대로_남는다() {
        // given
        Artist artist = 수정할_아티스트가_있다();
        별칭이_있다(artist, "방탄");

        // when
        artistService.update(아티스트_id, 수정_요청(null, null, null, null, true));

        // then
        assertSoftly(softly -> {
            softly.assertThat(artist.getName()).isEqualTo(아티스트_이름);
            softly.assertThat(artist.getGenre()).isEqualTo(ArtistGenre.DANCE);
            softly.assertThat(artist.getInstagramUrl()).isEqualTo(아티스트_인스타);
            softly.assertThat(artist.isNeedsReview()).isTrue();
        });
    }

    @Test
    void 이름을_자기_별칭과_같게_바꾸면_그_별칭을_지운다() {
        // 다른 아티스트의 별칭은 validateNameForUpdate가 막지만, 자기 별칭은 제외되어 통과한다.
        // 그대로 두면 대표명과 별칭이 같아져 임포트가 두 건을 찾는다.
        // given
        Artist artist = 수정할_아티스트가_있다();
        별칭이_있다(artist, "방탄");

        // when — 별칭은 보내지 않고 이름만 자기 별칭과 같게 바꾼다
        artistService.update(아티스트_id, 수정_요청("방탄", null, null, null, null));

        // then
        verify(artistAliasRepository).deleteByArtistIdAndName(아티스트_id, "방탄");
    }

    @Test
    void 별칭을_함께_보내면_이름과_같은_별칭은_따로_지우지_않는다() {
        // given
        Artist artist = 수정할_아티스트가_있다();
        별칭이_있다(artist, "방탄");

        // when
        artistService.update(아티스트_id, 수정_요청("방탄", List.of("방탄"), null, null, null));

        // then
        verify(artistAliasRepository, never()).deleteByArtistIdAndName(any(), any());
    }

    @Test
    void 없는_아티스트는_삭제할_수_없다() {
        // given
        given(artistRepository.findById(아티스트_id)).willReturn(Optional.empty());

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.delete(아티스트_id)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    void 출연_이력이_있는_아티스트는_삭제할_수_없다() {
        // given
        given(artistRepository.findById(아티스트_id))
                .willReturn(Optional.of(Artist.builder().name(아티스트_이름).build()));
        given(artistRepository.countAppearancesByArtistId(아티스트_id)).willReturn(1L);

        // when
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.delete(아티스트_id)
        );

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_HAS_APPEARANCES);
        verify(artistRepository, never()).deleteById(any());
    }

    @Test
    void 아티스트를_삭제하면_별칭을_먼저_지운다() {
        // given
        // 별칭이 아티스트를 FK로 참조하므로 순서가 뒤집히면 제약 위반이 난다.
        given(artistRepository.findById(아티스트_id))
                .willReturn(Optional.of(Artist.builder().name(아티스트_이름).build()));

        // when
        artistService.delete(아티스트_id);

        // then
        InOrder inOrder = inOrder(artistAliasRepository, artistRepository);
        inOrder.verify(artistAliasRepository).deleteByArtistId(아티스트_id);
        inOrder.verify(artistRepository).deleteById(아티스트_id);
    }

    private ArtistCreateRequest 등록_요청(String name, List<String> otherNames) {
        return new ArtistCreateRequest(name, otherNames, ArtistGenre.DANCE, null);
    }

    private ArtistUpdateRequest 수정_요청(String name, List<String> otherNames,
                                          ArtistGenre genre, String instagramUrl, Boolean needsReview) {
        return new ArtistUpdateRequest(name, otherNames, genre, instagramUrl, needsReview);
    }

    private void 저장은_받은_엔티티를_그대로_돌려준다() {
        given(artistRepository.save(any(Artist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private Artist 수정할_아티스트가_있다() {
        Artist artist = Artist.builder()
                .name(아티스트_이름)
                .genre(ArtistGenre.DANCE)
                .instagramUrl(아티스트_인스타)
                .needsReview(false)
                .build();
        given(artistRepository.findById(아티스트_id)).willReturn(Optional.of(artist));
        return artist;
    }

    private ArtistAlias 별칭이_있다(Artist artist, String name) {
        ArtistAlias alias = ArtistAlias.builder().artist(artist).name(name).build();
        given(artistAliasRepository.findByArtistId(any())).willReturn(List.of(alias));
        return alias;
    }
}
