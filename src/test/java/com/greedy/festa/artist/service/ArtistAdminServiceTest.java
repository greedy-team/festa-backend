package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistCreateRequest;
import com.greedy.festa.artist.dto.ArtistAdminSortType;
import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.ArtistUpdateRequest;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.support.fixture.ArtistFixture;
import com.greedy.festa.global.exception.FestaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ArtistAdminServiceTest {

    private ArtistRepository artistRepository;
    private ArtistAliasRepository artistAliasRepository;
    private ArtistAdminService service;

    @BeforeEach
    void setUp() {
        artistRepository = mock(ArtistRepository.class);
        artistAliasRepository = mock(ArtistAliasRepository.class);
        service = new ArtistAdminService(artistRepository, artistAliasRepository);
    }

    @Test
    void createsArtistWithoutReviewState() {
        given(artistRepository.save(any(Artist.class))).willAnswer(invocation -> invocation.getArgument(0));

        ArtistResponse response = service.create(new ArtistCreateRequest(
                "artist", List.of("artist alias"), ArtistGenre.BAND, "https://instagram.com/artist"));

        assertThat(response.name()).isEqualTo("artist");
        assertThat(response.otherNames()).containsExactly("artist alias");
        assertThat(response.genre()).isEqualTo(ArtistGenre.BAND);
        verify(artistRepository).save(any(Artist.class));
    }

    @Test
    void updatesNameGenreAndInstagramUrlWithoutReviewState() {
        Artist artist = ArtistFixture.artist("artist").genre(ArtistGenre.BAND).build();
        given(artistRepository.findById(1L)).willReturn(Optional.of(artist));
        given(artistAliasRepository.findByArtistId(1L)).willReturn(List.of());

        ArtistResponse response = service.update(1L, new ArtistUpdateRequest(
                "updated", null, ArtistGenre.HIPHOP, "https://instagram.com/updated"));

        assertThat(response.name()).isEqualTo("updated");
        assertThat(response.genre()).isEqualTo(ArtistGenre.HIPHOP);
        assertThat(response.instagramUrl()).isEqualTo("https://instagram.com/updated");
    }

    @Test
    void rejectsDuplicateArtistNameOnCreate() {
        given(artistRepository.existsByName("artist")).willReturn(true);

        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> service.create(new ArtistCreateRequest(
                        "artist", List.of(), ArtistGenre.BAND, null)));

        assertThat(exception.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_DUPLICATE_NAME);
        verify(artistRepository, never()).save(any());
    }

    @Test
    void preservesListQueryEscapingWithoutTheRemovedReviewFilter() {
        given(artistRepository.findAllWithAppearanceCount(eq(null), eq("100\\%\\_\\\\live"), any()))
                .willReturn(new PageImpl<>(List.of()));

        service.findAll("  100 % _ \\ live  ", null, ArtistAdminSortType.NAME, 0, 20);

        verify(artistRepository).findAllWithAppearanceCount(
                eq(null), eq("100\\%\\_\\\\live"), any());
    }
}
