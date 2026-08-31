package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.global.exception.FestaException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AdminSingleLookupServiceTest {

    @Test
    void artistReturnsCurrentAdminValues() {
        ArtistRepository repository = mock(ArtistRepository.class);
        ArtistAliasRepository aliasRepository = mock(ArtistAliasRepository.class);
        Artist artist = Artist.builder().name("아이유").instagramUrl("instagram").build();
        given(repository.findById(1L)).willReturn(Optional.of(artist));
        given(repository.countAppearancesByArtistId(1L)).willReturn(3L);
        given(aliasRepository.findByArtistId(1L)).willReturn(List.of());

        ArtistResponse response = new ArtistAdminService(repository, aliasRepository).findOne(1L);

        assertThat(response.name()).isEqualTo("아이유");
        assertThat(response.appearanceCount()).isEqualTo(3);
    }

    @Test
    void missingArtistUsesExistingErrorContract() {
        ArtistRepository repository = mock(ArtistRepository.class);
        given(repository.findById(1L)).willReturn(Optional.empty());

        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> new ArtistAdminService(repository, mock(ArtistAliasRepository.class)).findOne(1L));

        assertThat(exception.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }
}
