package com.greedy.festa.search.dto;

import java.util.List;

public record SearchResponse(
        String query,
        SearchType selectedType,
        SearchCounts counts,
        List<SearchFestivalResponse> festivals,
        List<SearchArtistResponse> artists,
        List<SearchHostResponse> hosts,
        List<String> relatedKeywords
) {

    public static SearchResponse of(
            String query,
            SearchType selectedType,
            SearchCounts counts,
            List<SearchFestivalResponse> festivals,
            List<SearchArtistResponse> artists,
            List<SearchHostResponse> hosts
    ) {
        return new SearchResponse(
                query, selectedType, counts, festivals, artists, hosts, List.of());
    }
}
