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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

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
        SearchType type = SearchType.from(typeValue);
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.KST));

        List<SearchArtistResponse> artists = includes(type, SearchType.ARTIST)
                ? findArtists(likeQueries.getFirst(), today) : List.of();
        List<SearchHostResponse> hosts = includes(type, SearchType.HOST)
                ? findHosts(likeQueries) : List.of();
        List<SearchFestivalResponse> festivals = includes(type, SearchType.FESTIVAL)
                ? findFestivals(likeQueries) : List.of();

        long artistCount = includes(type, SearchType.ARTIST)
                ? artists.size() : artistRepository.countSearchRows(likeQueries.getFirst());
        long hostCount = includes(type, SearchType.HOST)
                ? hosts.size() : countHosts(likeQueries);
        long festivalCount = includes(type, SearchType.FESTIVAL)
                ? festivals.size() : countFestivals(likeQueries);
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

    private List<SearchHostResponse> findHosts(List<String> queries) {
        return distinctById(
                queries.stream()
                        .flatMap(query -> hostRepository.findSearchRows(query).stream())
                        .map(SearchHostResponse::from)
                        .toList(),
                SearchHostResponse::hostId
        ).stream()
                .sorted(Comparator.comparing(SearchHostResponse::hostId))
                .toList();
    }

    private List<SearchFestivalResponse> findFestivals(List<String> queries) {
        return distinctById(
                queries.stream()
                        .flatMap(query -> festivalRepository.findPublishedSearchRows(query).stream())
                        .map(SearchFestivalResponse::from)
                        .toList(),
                SearchFestivalResponse::festivalId
        ).stream()
                .sorted(Comparator.comparing(SearchFestivalResponse::startDate).reversed()
                        .thenComparing(SearchFestivalResponse::festivalId))
                .toList();
    }

    private long countHosts(List<String> queries) {
        return queries.size() == 1
                ? hostRepository.countSearchRows(queries.getFirst())
                : findHosts(queries).size();
    }

    private long countFestivals(List<String> queries) {
        return queries.size() == 1
                ? festivalRepository.countPublishedSearchRows(queries.getFirst())
                : findFestivals(queries).size();
    }

    private <T> List<T> distinctById(List<T> items, Function<T, Long> idExtractor) {
        LinkedHashMap<Long, T> distinctItems = new LinkedHashMap<>();
        items.forEach(item -> distinctItems.putIfAbsent(idExtractor.apply(item), item));
        return List.copyOf(distinctItems.values());
    }

    private boolean includes(SearchType selected, SearchType target) {
        return selected == SearchType.ALL || selected == target;
    }
}
