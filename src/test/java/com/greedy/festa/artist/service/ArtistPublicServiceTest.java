package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistDetailResponse;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRecentFestivalRow;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.ArtistWithAppearanceCount;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ArtistPublicServiceTest {

    private ArtistRepository artistRepository;
    private ArtistAliasRepository artistAliasRepository;
    private LineupRepository lineupRepository;
    private ArtistService artistService;

    @BeforeEach
    void setUp() {
        artistRepository = mock(ArtistRepository.class);
        artistAliasRepository = mock(ArtistAliasRepository.class);
        lineupRepository = mock(LineupRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T15:30:00Z"), ZoneOffset.UTC);
        artistService = new ArtistService(
                artistRepository, artistAliasRepository, lineupRepository, clock);
    }

    @ParameterizedTest
    @CsvSource({"-1,10,INVALID_PAGE", "0,0,INVALID_PAGE_SIZE", "0,51,INVALID_PAGE_SIZE"})
    void 잘못된_페이지_파라미터를_거부한다(int page, int size, String errorCode) {
        FestaException thrown = catchThrowableOfType(
                FestaException.class,
                () -> artistService.findAll(page, size, null, "APPEARANCES", null));

        assertThat(thrown.getErrorCode().name()).isEqualTo(errorCode);
    }

    @Test
    void 잘못된_장르_정렬_긴_검색어를_각각_계약된_코드로_거부한다() {
        FestaException genre = catchThrowableOfType(FestaException.class,
                () -> artistService.findAll(0, 10, "ROCK", "APPEARANCES", null));
        FestaException sort = catchThrowableOfType(FestaException.class,
                () -> artistService.findAll(0, 10, null, "RECENT", null));
        FestaException query = catchThrowableOfType(FestaException.class,
                () -> artistService.findAll(0, 10, null, "NAME", "가".repeat(51)));

        assertThat(genre.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_GENRE_TYPE);
        assertThat(sort.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_SORT_TYPE);
        assertThat(query.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_QUERY);
    }

    @Test
    void KST의_오늘을_목록_쿼리에_전달하고_기본_정렬은_출연순이다() {
        given(artistRepository.findPublicByAppearances(
                eq(null), eq(null), any(LocalDate.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        artistService.findAll(0, 10, null, null, "   ");

        ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
        verify(artistRepository).findPublicByAppearances(
                eq(null), eq(null), today.capture(), any(Pageable.class));
        assertThat(today.getValue()).isEqualTo(LocalDate.of(2026, 8, 27));
    }

    @Test
    void 검색어의_LIKE_와일드카드는_리터럴로_이스케이프한다() {
        given(artistRepository.findPublicByAppearances(
                eq(null), eq("100\\%\\_live"), any(LocalDate.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        artistService.findAll(0, 10, null, "APPEARANCES", "100%_live");

        verify(artistRepository).findPublicByAppearances(
                eq(null), eq("100\\%\\_live"), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    void 현재_페이지의_최근_축제는_아티스트별_반복_조회_없이_한번에_가져온다() {
        Artist first = artist(1L, "BTS");
        Artist second = artist(2L, "잔나비");
        ArtistWithAppearanceCount firstRow = appearanceRow(first, 2L);
        ArtistWithAppearanceCount secondRow = appearanceRow(second, 1L);
        given(artistRepository.findPublicByAppearances(
                eq(null), eq(null), any(LocalDate.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(
                        List.of(firstRow, secondRow), PageRequest.of(0, 10), 2));
        ArtistRecentFestivalRow recent = mock(ArtistRecentFestivalRow.class);
        given(recent.getArtistId()).willReturn(1L);
        given(recent.getFestivalId()).willReturn(10L);
        given(recent.getFestivalName()).willReturn("대동제");
        given(recent.getHostShortName()).willReturn("한국대");
        given(artistRepository.findRecentFestivals(
                eq(List.of(1L, 2L)), any(LocalDate.class)))
                .willReturn(List.of(recent));

        var response = artistService.findAll(0, 10, null, "APPEARANCES", null);

        assertThat(response.items().getFirst().recentFestival().festivalId()).isEqualTo(10L);
        assertThat(response.items().get(1).recentFestival()).isNull();
        verify(artistRepository).findRecentFestivals(
                eq(List.of(1L, 2L)), any(LocalDate.class));
    }

    @Test
    void 공개_목록과_상세의_imageUrl은_DB값과_무관하게_null이다() {
        Artist artist = Artist.builder()
                .name("BTS")
                .genre(ArtistGenre.DANCE)
                .imageUrl("https://example.com/portrait.jpg")
                .build();
        ReflectionTestUtils.setField(artist, "id", 1L);
        ArtistWithAppearanceCount row = appearanceRow(artist, 0L);
        given(artistRepository.findPublicByAppearances(
                eq(null), eq(null), any(LocalDate.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));
        given(artistRepository.findRecentFestivals(
                eq(List.of(1L)), any(LocalDate.class)))
                .willReturn(List.of());
        given(artistRepository.findById(1L)).willReturn(Optional.of(artist));
        given(artistAliasRepository.findByArtistId(1L)).willReturn(List.of());
        given(lineupRepository.findPublishedByArtistId(1L)).willReturn(List.of());

        var list = artistService.findAll(0, 10, null, "APPEARANCES", null);
        ArtistDetailResponse detail = artistService.findById(1L);

        assertThat(list.items().getFirst().imageUrl()).isNull();
        assertThat(detail.imageUrl()).isNull();
    }

    @Test
    void 상세는_예정_공연과_종료된_축제_이력을_KST_기준으로_나눈다() {
        Artist artist = artist(1L, "BTS");
        Host host = host(1L, "한국대학교");
        Festival todayFestival = festival(10L, host, "오늘 축제",
                LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 28));
        Festival pastFestival = festival(11L, host, "과거 축제",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));

        given(artistRepository.findById(1L)).willReturn(Optional.of(artist));
        given(artistAliasRepository.findByArtistId(1L)).willReturn(List.of());
        given(lineupRepository.findPublishedByArtistId(1L)).willReturn(List.of(
                Lineup.builder().artist(artist).festival(todayFestival).day(1).displayOrder(1).build(),
                Lineup.builder().artist(artist).festival(pastFestival).day(1).displayOrder(1).build()));

        ArtistDetailResponse response = artistService.findById(1L);

        assertThat(response.upcomingShows().total()).isEqualTo(1);
        assertThat(response.upcomingShows().items().getFirst().performanceDate())
                .isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(response.upcomingShows().items().getFirst().dday()).isZero();
        assertThat(response.appearances().total()).isEqualTo(1);
        assertThat(response.appearances().items().getFirst().festivalId()).isEqualTo(11L);
    }

    @Test
    void 축제_기간을_벗어난_라인업_day는_예정_공연에_노출하지_않는다() {
        Artist artist = artist(1L, "BTS");
        Host host = host(1L, "한국대학교");
        Festival endedFestival = festival(10L, host, "종료 축제",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22));

        given(artistRepository.findById(1L)).willReturn(Optional.of(artist));
        given(artistAliasRepository.findByArtistId(1L)).willReturn(List.of());
        given(lineupRepository.findPublishedByArtistId(1L)).willReturn(List.of(
                Lineup.builder().artist(artist).festival(endedFestival)
                        .day(10).displayOrder(1).build()));

        ArtistDetailResponse response = artistService.findById(1L);

        assertThat(response.upcomingShows().items()).isEmpty();
        assertThat(response.upcomingShows().total()).isZero();
        assertThat(response.appearances().items())
                .extracting(item -> item.festivalId())
                .containsExactly(10L);
    }

    @Test
    void 상세_섹션은_최대_5건만_반환하고_total은_자르기_전_건수다() {
        Artist artist = artist(1L, "BTS");
        Host host = host(1L, "한국대학교");
        List<Lineup> lineups = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(day -> Lineup.builder()
                        .artist(artist)
                        .festival(festival((long) day, host, "축제 " + day,
                                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)))
                        .day(day)
                        .displayOrder(day)
                        .build())
                .toList();
        given(artistRepository.findById(1L)).willReturn(Optional.of(artist));
        given(artistAliasRepository.findByArtistId(1L)).willReturn(List.of());
        given(lineupRepository.findPublishedByArtistId(1L)).willReturn(lineups);

        ArtistDetailResponse response = artistService.findById(1L);

        assertThat(response.upcomingShows().items()).hasSize(5);
        assertThat(response.upcomingShows().total()).isEqualTo(6);
    }

    @Test
    void 없는_아티스트는_ARTIST_NOT_FOUND다() {
        given(artistRepository.findById(99L)).willReturn(Optional.empty());

        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> artistService.findById(99L));

        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    private Artist artist(Long id, String name) {
        Artist artist = Artist.builder().name(name).genre(ArtistGenre.DANCE).build();
        ReflectionTestUtils.setField(artist, "id", id);
        return artist;
    }

    private ArtistWithAppearanceCount appearanceRow(Artist artist, long count) {
        ArtistWithAppearanceCount row = mock(ArtistWithAppearanceCount.class);
        given(row.getArtist()).willReturn(artist);
        given(row.getAppearanceCount()).willReturn(count);
        return row;
    }

    private Host host(Long id, String name) {
        Host host = Host.builder().name(name).shortName(name).region("서울").build();
        ReflectionTestUtils.setField(host, "id", id);
        return host;
    }

    private Festival festival(Long id, Host host, String name, LocalDate start, LocalDate end) {
        Festival festival = Festival.builder()
                .host(host)
                .name(name)
                .startDate(start)
                .endDate(end)
                .build();
        festival.publish(Instant.parse("2026-08-01T00:00:00Z"));
        ReflectionTestUtils.setField(festival, "id", id);
        return festival;
    }
}
