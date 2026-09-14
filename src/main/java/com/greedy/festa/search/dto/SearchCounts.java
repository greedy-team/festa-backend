package com.greedy.festa.search.dto;

public record SearchCounts(long all, long festival, long artist, long host) {

    public static SearchCounts of(long festival, long artist, long host) {
        return new SearchCounts(festival + artist + host, festival, artist, host);
    }
}
