package com.greedy.festa.search.controller;

import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchControllerTest {

    private SearchService searchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(
                mock(ArtistRepository.class),
                mock(HostRepository.class),
                mock(FestivalRepository.class),
                Clock.fixed(Instant.parse("2026-08-26T15:30:00Z"), ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 검색어와_유형을_서비스에_전달하고_통합_응답을_반환한다() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "봄").param("type", "ARTIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("봄"))
                .andExpect(jsonPath("$.selectedType").value("ARTIST"))
                .andExpect(jsonPath("$.counts.all").value(0))
                .andExpect(jsonPath("$.festivals").isArray())
                .andExpect(jsonPath("$.artists").isArray())
                .andExpect(jsonPath("$.hosts").isArray())
                .andExpect(jsonPath("$.relatedKeywords").isArray());

    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void 잘못된_검색어는_4필드_검색_오류로_반환한다(MultiValueMap<String, String> params)
            throws Exception {
        mockMvc.perform(get("/api/search").params(params))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SEARCH_INVALID_QUERY"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/search"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "artist", " ARTIST "})
    void 기본값_소문자_trim을_포함한_type을_계약대로_해석한다(String type) throws Exception {
        String expected = type.isBlank() ? "ALL" : "ARTIST";

        mockMvc.perform(get("/api/search").param("q", "봄").param("type", type))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedType").value(expected));
    }

    @Test
    void 지원하지_않는_type은_4필드_검색_오류로_반환한다() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "봄").param("type", "SCHOOL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SEARCH_INVALID_TYPE"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/search"));
    }

    static Stream<MultiValueMap<String, String>> invalidQueries() {
        LinkedMultiValueMap<String, String> missing = new LinkedMultiValueMap<>();
        LinkedMultiValueMap<String, String> empty = new LinkedMultiValueMap<>();
        empty.add("q", "");
        LinkedMultiValueMap<String, String> blank = new LinkedMultiValueMap<>();
        blank.add("q", "   ");
        return Stream.of(missing, empty, blank);
    }
}
