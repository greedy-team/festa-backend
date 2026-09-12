package com.greedy.festa.search.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.ArtistSearchRow;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.host.repository.HostSearchRow;
import com.greedy.festa.search.dto.SearchResponse;
import com.greedy.festa.search.dto.SearchCounts;
import com.greedy.festa.search.dto.SearchType;
import com.greedy.festa.search.exception.SearchErrorCode;
import com.greedy.festa.support.fixture.ArtistFixture;
import com.greedy.festa.support.fixture.FestivalFixture;
import com.greedy.festa.support.fixture.Fixtures;
import com.greedy.festa.support.fixture.HostFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SearchServiceTest {

    private ArtistRepository artistRepository;
    private HostRepository hostRepository;
    private FestivalRepository festivalRepository;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        artistRepository = mock(ArtistRepository.class);
        hostRepository = mock(HostRepository.class);
        festivalRepository = mock(FestivalRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T15:30:00Z"), ZoneOffset.UTC);
        searchService = new SearchService(
                artistRepository, hostRepository, festivalRepository, clock);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 빈_검색어는_계약_오류로_거절한다(String query) {
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> searchService.search(query, null));

        assertThat(thrown.getErrorCode()).isEqualTo(SearchErrorCode.SEARCH_INVALID_QUERY);
        verifyNoInteractions(artistRepository, hostRepository, festivalRepository);
    }

    @Test
    void trim_후_50자_검색어는_허용한다() {
        String query = "가".repeat(50);
        given(artistRepository.findSearchRows(query, LocalDate.of(2026, 8, 27)))
                .willReturn(List.of());
        given(hostRepository.findSearchRows(query)).willReturn(List.of());
        given(festivalRepository.findPublishedSearchRows(query)).willReturn(List.of());

        SearchResponse response = searchService.search("  " + query + "  ", null);

        assertThat(response.query()).isEqualTo(query);
        verify(artistRepository).findSearchRows(query, LocalDate.of(2026, 8, 27));
        verify(hostRepository).findSearchRows(query);
        verify(festivalRepository).findPublishedSearchRows(query);
    }

    @Test
    void 응답의_공백은_보존하고_조회와_count에는_정규화된_검색어를_전달한다() {
        String pattern = "a\\%\\_\\\\b";
        SearchResponse response = searchService.search("  a % _ \\ b  ", "HOST");

        assertThat(response.query()).isEqualTo("a % _ \\ b");
        verify(hostRepository).findSearchRows(pattern);
        verify(artistRepository).countSearchRows(pattern);
        verify(festivalRepository).countPublishedSearchRows(pattern);
    }

    @Test
    void trim_후_51자_검색어는_계약_오류로_거절한다() {
        String query = "  " + "가".repeat(51) + "  ";

        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> searchService.search(query, null));

        assertThat(thrown.getErrorCode()).isEqualTo(SearchErrorCode.SEARCH_INVALID_QUERY);
        verifyNoInteractions(artistRepository, hostRepository, festivalRepository);
    }

    @Test
    void 지원하지_않는_검색_유형은_계약_오류로_거절한다() {
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> searchService.search("봄", "SCHOOL"));

        assertThat(thrown.getErrorCode()).isEqualTo(SearchErrorCode.SEARCH_INVALID_TYPE);
        verifyNoInteractions(artistRepository, hostRepository, festivalRepository);
    }

    @Test
    void 결과가_없으면_빈_그룹과_0인_count를_반환한다() {
        given(artistRepository.findSearchRows("없는검색어", LocalDate.of(2026, 8, 27)))
                .willReturn(List.of());
        given(hostRepository.findSearchRows("없는검색어")).willReturn(List.of());
        given(festivalRepository.findPublishedSearchRows("없는검색어")).willReturn(List.of());

        SearchResponse response = searchService.search(" 없는검색어 ", null);

        assertThat(response.query()).isEqualTo("없는검색어");
        assertThat(response.selectedType()).isEqualTo(SearchType.ALL);
        assertThat(response.counts().all()).isZero();
        assertThat(response.festivals()).isEmpty();
        assertThat(response.artists()).isEmpty();
        assertThat(response.hosts()).isEmpty();
        assertThat(response.relatedKeywords()).isEmpty();
    }

    @Test
    void 유형을_선택하면_count는_전체_대상이고_해당_그룹만_채운다() {
        Artist artist = Fixtures.withId(ArtistFixture.artist("봄날").build(), 1L);
        ArtistSearchRow artistRow = mock(ArtistSearchRow.class);
        given(artistRow.getArtist()).willReturn(artist);
        given(artistRow.getAppearanceCount()).willReturn(2L);
        given(artistRow.getLatestAppearanceDate()).willReturn(LocalDate.of(2026, 5, 10));
        HostSearchRow hostRow = mock(HostSearchRow.class);
        given(hostRow.getHost()).willReturn(HostFixture.host("봄대학교").build());
        given(hostRow.getFestivalCount()).willReturn(1L);
        given(artistRepository.findSearchRows("봄", LocalDate.of(2026, 8, 27)))
                .willReturn(List.of(artistRow));
        given(hostRepository.findSearchRows("봄")).willReturn(List.of(hostRow));
        given(festivalRepository.findPublishedSearchRows("봄")).willReturn(List.of());
        given(hostRepository.countSearchRows("봄")).willReturn(1L);
        given(festivalRepository.countPublishedSearchRows("봄")).willReturn(0L);

        SearchResponse response = searchService.search("봄", "artist");

        assertThat(response.selectedType()).isEqualTo(SearchType.ARTIST);
        assertThat(response.counts().all()).isEqualTo(2);
        assertThat(response.counts().artist()).isEqualTo(1);
        assertThat(response.counts().host()).isEqualTo(1);
        assertThat(response.artists()).hasSize(1);
        assertThat(response.artists().getFirst().imageUrl()).isNull();
        assertThat(response.hosts()).isEmpty();
        assertThat(response.festivals()).isEmpty();
        verify(hostRepository, never()).findSearchRows("봄");
        verify(festivalRepository, never()).findPublishedSearchRows("봄");
        verify(artistRepository, never()).countSearchRows("봄");
        verify(hostRepository).countSearchRows("봄");
        verify(festivalRepository).countPublishedSearchRows("봄");
    }

    @Test
    void HOST_선택은_Host_목록과_나머지_count만_조회한다() {
        Host host = Fixtures.withId(HostFixture.host("봄대학교").build(), 1L);
        HostSearchRow hostRow = mock(HostSearchRow.class);
        given(hostRow.getHost()).willReturn(host);
        given(hostRow.getLatestFestivalDate()).willReturn(LocalDate.of(2026, 8, 31));
        given(hostRepository.findSearchRows("봄")).willReturn(List.of(hostRow));
        given(artistRepository.countSearchRows("봄")).willReturn(2L);
        given(festivalRepository.countPublishedSearchRows("봄")).willReturn(3L);

        SearchResponse response = searchService.search("봄", "HOST");

        assertThat(response.counts()).isEqualTo(new SearchCounts(6, 3, 2, 1));
        assertThat(response.hosts().getFirst().hostId()).isEqualTo(1L);
        assertThat(response.hosts().getFirst().latestFestivalYearMonth()).isEqualTo("2026-08");
        verify(hostRepository).findSearchRows("봄");
        verify(artistRepository).countSearchRows("봄");
        verify(festivalRepository).countPublishedSearchRows("봄");
        verify(artistRepository, never()).findSearchRows("봄", LocalDate.of(2026, 8, 27));
        verify(festivalRepository, never()).findPublishedSearchRows("봄");
        verify(hostRepository, never()).countSearchRows("봄");
    }

    @Test
    void FESTIVAL_선택은_Festival_목록과_나머지_count만_조회한다() {
        given(festivalRepository.findPublishedSearchRows("봄")).willReturn(List.of());
        given(artistRepository.countSearchRows("봄")).willReturn(2L);
        given(hostRepository.countSearchRows("봄")).willReturn(4L);

        SearchResponse response = searchService.search("봄", "FESTIVAL");

        assertThat(response.counts()).isEqualTo(new SearchCounts(6, 0, 2, 4));
        verify(festivalRepository).findPublishedSearchRows("봄");
        verify(artistRepository).countSearchRows("봄");
        verify(hostRepository).countSearchRows("봄");
        verify(artistRepository, never()).findSearchRows("봄", LocalDate.of(2026, 8, 27));
        verify(hostRepository, never()).findSearchRows("봄");
        verify(festivalRepository, never()).countPublishedSearchRows("봄");
    }

    @Test
    void 약어_확장_결과를_병합해도_Host는_id_오름차순으로_정렬한다() {
        Host directMatch = Fixtures.withId(HostFixture.host("한국외국어대학교 서울캠퍼스")
                .shortName("외대")
                .build(), 90L);
        Host officialNameMatch = Fixtures.withId(HostFixture.host("한국외국어대학교")
                .build(), 5L);
        HostSearchRow directRow = mock(HostSearchRow.class);
        given(directRow.getHost()).willReturn(directMatch);
        given(directRow.getFestivalCount()).willReturn(0L);
        HostSearchRow officialRow = mock(HostSearchRow.class);
        given(officialRow.getHost()).willReturn(officialNameMatch);
        given(officialRow.getFestivalCount()).willReturn(0L);
        given(hostRepository.findSearchRows("외대")).willReturn(List.of(directRow));
        given(hostRepository.findSearchRows("한국외국어대학교"))
                .willReturn(List.of(officialRow, directRow));

        SearchResponse response = searchService.search("외대", "HOST");

        assertThat(response.hosts()).extracting(item -> item.hostId())
                .containsExactly(officialNameMatch.getId(), directMatch.getId());
        assertThat(response.counts().host()).isEqualTo(2);
        verify(hostRepository).findSearchRows("외대");
        verify(hostRepository).findSearchRows("한국외국어대학교");
    }

    @Test
    void 약어_확장_결과를_병합해도_축제는_개최일_내림차순으로_정렬한다() {
        Host host = Fixtures.withId(HostFixture.host("건국대학교").shortName("건대").build(), 1L);
        Festival directMatch = Fixtures.withId(FestivalFixture.festival("직접 일치 축제")
                .host(host)
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 3))
                .build(), 1L);
        Festival officialNameMatch = Fixtures.withId(FestivalFixture.festival("정식명 일치 축제")
                .host(host)
                .startDate(LocalDate.of(2026, 9, 20))
                .endDate(LocalDate.of(2026, 9, 22))
                .build(), 2L);
        given(festivalRepository.findPublishedSearchRows("건대"))
                .willReturn(List.of(directMatch));
        given(festivalRepository.findPublishedSearchRows("건국대학교"))
                .willReturn(List.of(officialNameMatch, directMatch));

        SearchResponse response = searchService.search("건대", "FESTIVAL");

        assertThat(response.festivals()).extracting(item -> item.festivalId())
                .containsExactly(officialNameMatch.getId(), directMatch.getId());
        assertThat(response.counts().festival()).isEqualTo(2);
    }

    @Test
    void LIKE_와일드카드는_리터럴로_검색한다() {
        given(artistRepository.findSearchRows("100\\%\\_live", LocalDate.of(2026, 8, 27)))
                .willReturn(List.of());
        given(hostRepository.findSearchRows("100\\%\\_live")).willReturn(List.of());
        given(festivalRepository.findPublishedSearchRows("100\\%\\_live")).willReturn(List.of());

        searchService.search("100%_live", "ALL");

        verify(artistRepository).findSearchRows("100\\%\\_live", LocalDate.of(2026, 8, 27));
        verify(hostRepository).findSearchRows("100\\%\\_live");
        verify(festivalRepository).findPublishedSearchRows("100\\%\\_live");
        verify(artistRepository, never()).countSearchRows("100\\%\\_live");
        verify(hostRepository, never()).countSearchRows("100\\%\\_live");
        verify(festivalRepository, never()).countPublishedSearchRows("100\\%\\_live");
    }
}
