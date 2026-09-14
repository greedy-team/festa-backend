package com.greedy.festa.search.service;

import java.util.List;
import java.util.Map;

/**
 * The deliberately small, product-approved set of Host abbreviations.
 *
 * <p>This is not a general abbreviation rule: aliases not listed here must not be inferred.</p>
 */
final class HostSearchAliases {

    private static final Map<String, String> OFFICIAL_NAME_BY_ALIAS = Map.ofEntries(
            Map.entry("연대", "연세대학교"),
            Map.entry("고대", "고려대학교"),
            Map.entry("홍대", "홍익대학교"),
            Map.entry("외대", "한국외국어대학교"),
            Map.entry("한국외대", "한국외국어대학교"),
            Map.entry("이대", "이화여자대학교"),
            Map.entry("이화여대", "이화여자대학교"),
            Map.entry("숙대", "숙명여자대학교"),
            Map.entry("숙명여대", "숙명여자대학교"),
            Map.entry("서울여대", "서울여자대학교"),
            Map.entry("설여대", "서울여자대학교"),
            Map.entry("동덕여대", "동덕여자대학교"),
            Map.entry("동덕대", "동덕여자대학교"),
            Map.entry("덕성여대", "덕성여자대학교"),
            Map.entry("덕성대", "덕성여자대학교"),
            Map.entry("건대", "건국대학교")
    );

    private HostSearchAliases() {
    }

    static List<String> queriesFor(String query) {
        String officialName = OFFICIAL_NAME_BY_ALIAS.get(query.replace(" ", ""));
        return officialName == null ? List.of(query) : List.of(query, officialName);
    }
}
