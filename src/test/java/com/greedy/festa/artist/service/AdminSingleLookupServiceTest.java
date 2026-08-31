package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.LineupAdminDetailResponse;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.exception.LineupErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.exception.FestaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

    @Test
    void lineupReturnsFestivalArtistAndPosition() {
        LineupRepository repository = mock(LineupRepository.class);
        Festival festival = Festival.builder().name("대동제")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 2)).build();
        Lineup lineup = Lineup.builder().festival(festival)
                .artist(Artist.builder().name("아이유").build()).day(2).displayOrder(3).build();
        given(repository.findDetailById(1L)).willReturn(Optional.of(lineup));

        LineupAdminDetailResponse response = new LineupAdminService(repository, mock(ArtistRepository.class)).findOne(1L);

        assertThat(response.festivalName()).isEqualTo("대동제");
        assertThat(response.artistName()).isEqualTo("아이유");
        assertThat(response.day()).isEqualTo(2);
        assertThat(response.displayOrder()).isEqualTo(3);
    }

    @Test
    void missingLineupUsesLineupNotFound() {
        LineupRepository repository = mock(LineupRepository.class);
        given(repository.findDetailById(1L)).willReturn(Optional.empty());

        FestaException exception = catchThrowableOfType(FestaException.class,
                () -> new LineupAdminService(repository, mock(ArtistRepository.class)).findOne(1L));

        assertThat(exception.getErrorCode()).isEqualTo(LineupErrorCode.LINEUP_NOT_FOUND);
    }
}
