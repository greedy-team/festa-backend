package com.greedy.festa.search.dto;

import com.greedy.festa.global.util.EnumParser;
import com.greedy.festa.search.exception.SearchErrorCode;

public enum SearchType {
    ALL,
    ARTIST,
    HOST,
    FESTIVAL;

    public static SearchType from(String value) {
        return EnumParser.parse(SearchType.class, value, ALL, SearchErrorCode.SEARCH_INVALID_TYPE);
    }
}
