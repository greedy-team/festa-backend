package com.greedy.festa.search.service;

import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.ClockConfig;
import com.greedy.festa.global.exception.FestaException;
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
        String normalizedQuery = normalizeQuery(query);
        String likeQuery = escapeLikePattern(normalizedQuery);
        SearchType type = SearchType.from(typeValue);
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.KST));

        List<SearchArtistResponse> artists = includes(type, SearchType.ARTIST)
                ? findArtists(likeQuery, today) : List.of();
        List<SearchHostResponse> hosts = includes(type, SearchType.HOST)
                ? findHosts(likeQuery) : List.of();
        List<SearchFestivalResponse> festivals = includes(type, SearchType.FESTIVAL)
                ? findFestivals(likeQuery) : List.of();

        long artistCount = includes(type, SearchType.ARTIST)
                ? artists.size() : artistRepository.countSearchRows(likeQuery);
        long hostCount = includes(type, SearchType.HOST)
                ? hosts.size() : hostRepository.countSearchRows(likeQuery);
        long festivalCount = includes(type, SearchType.FESTIVAL)
                ? festivals.size() : festivalRepository.countPublishedSearchRows(likeQuery);
        SearchCounts counts = SearchCounts.of(festivalCount, artistCount, hostCount);
        return new SearchResponse(
                normalizedQuery,
                type,
                counts,
                festivals,
                artists,
                hosts,
                List.of()
        );
    }

    private List<SearchArtistResponse> findArtists(String query, LocalDate today) {
        return artistRepository.findSearchRows(query, today).stream()
                .map(SearchArtistResponse::from)
                .toList();
    }

    private List<SearchHostResponse> findHosts(String query) {
        return hostRepository.findSearchRows(query).stream()
                .map(SearchHostResponse::from)
                .toList();
    }

    private List<SearchFestivalResponse> findFestivals(String query) {
        return festivalRepository.findPublishedSearchRows(query).stream()
                .map(SearchFestivalResponse::from)
                .toList();
    }

    private String normalizeQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new FestaException(SearchErrorCode.SEARCH_INVALID_QUERY);
        }
        return query.trim();
    }

    private String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private boolean includes(SearchType selected, SearchType target) {
        return selected == SearchType.ALL || selected == target;
    }
}
