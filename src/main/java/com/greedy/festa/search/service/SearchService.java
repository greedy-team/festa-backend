package com.greedy.festa.search.service;

import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.ClockConfig;
import com.greedy.festa.global.util.LikePatternUtils;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.search.dto.SearchArtistResponse;
import com.greedy.festa.search.dto.SearchCounts;
import com.greedy.festa.search.dto.SearchFestivalResponse;
import com.greedy.festa.search.dto.SearchHostResponse;
import com.greedy.festa.search.dto.SearchResponse;
import com.greedy.festa.search.dto.SearchType;
import com.greedy.festa.search.exception.SearchErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ArtistRepository artistRepository;
    private final HostRepository hostRepository;
    private final FestivalRepository festivalRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SearchResponse search(String query, String typeValue) {
        String normalizedQuery = LikePatternUtils.normalizeRequiredQuery(
                query, 50, SearchErrorCode.SEARCH_INVALID_QUERY);
        List<String> likeQueries = HostSearchAliases.queriesFor(normalizedQuery).stream()
                .map(LikePatternUtils::toSearchPattern)
                .toList();
        String primaryQuery = likeQueries.getFirst();
        String aliasQuery = likeQueries.getLast();
        SearchType type = SearchType.from(typeValue);
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.KST));

        List<SearchArtistResponse> artists = includes(type, SearchType.ARTIST)
                ? findArtists(primaryQuery, today) : List.of();
        List<SearchHostResponse> hosts = includes(type, SearchType.HOST)
                ? findHosts(primaryQuery, aliasQuery) : List.of();
        List<SearchFestivalResponse> festivals = includes(type, SearchType.FESTIVAL)
                ? findFestivals(primaryQuery, aliasQuery) : List.of();

        long artistCount = includes(type, SearchType.ARTIST)
                ? artists.size() : artistRepository.countSearchRows(primaryQuery);
        long hostCount = includes(type, SearchType.HOST)
                ? hosts.size() : hostRepository.countSearchRows(primaryQuery, aliasQuery);
        long festivalCount = includes(type, SearchType.FESTIVAL)
                ? festivals.size() : festivalRepository.countPublishedSearchRows(primaryQuery, aliasQuery);
        SearchCounts counts = SearchCounts.of(festivalCount, artistCount, hostCount);
        return SearchResponse.of(
                normalizedQuery,
                type,
                counts,
                festivals,
                artists,
                hosts
        );
    }

    private List<SearchArtistResponse> findArtists(String query, LocalDate today) {
        return artistRepository.findSearchRows(query, today).stream()
                .map(SearchArtistResponse::from)
                .toList();
    }

    private List<SearchHostResponse> findHosts(String primaryQuery, String aliasQuery) {
        return hostRepository.findSearchRows(primaryQuery, aliasQuery).stream()
                .map(SearchHostResponse::from)
                .toList();
    }

    private List<SearchFestivalResponse> findFestivals(String primaryQuery, String aliasQuery) {
        return festivalRepository.findPublishedSearchRows(primaryQuery, aliasQuery).stream()
                .map(SearchFestivalResponse::from)
                .toList();
    }

    private boolean includes(SearchType selected, SearchType target) {
        return selected == SearchType.ALL || selected == target;
    }
}
