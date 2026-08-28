package com.greedy.festa.artist.controller;

import com.greedy.festa.artist.dto.ArtistDetailResponse;
import com.greedy.festa.artist.dto.ArtistListItemResponse;
import com.greedy.festa.artist.dto.ArtistSectionResponse;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.service.ArtistService;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArtistControllerTest {

    private ArtistService artistService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        artistService = mock(ArtistService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ArtistController(artistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 목록은_요청한_페이지와_크기로_기본_출연순을_조회한다() throws Exception {
        ArtistListItemResponse item = new ArtistListItemResponse(
                1L, "BTS", null, ArtistGenre.DANCE, 3, null);
        given(artistService.findAll(0, 10, null, "APPEARANCES", null))
                .willReturn(new PageResponse<>(List.of(item), 0, 10, 1, 1, false, false));

        mockMvc.perform(get("/api/artists")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].artistId").value(1))
                .andExpect(jsonPath("$.items[0].appearanceCount").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));

        verify(artistService).findAll(0, 10, null, "APPEARANCES", null);
    }

    @Test
    void page와_size는_필수이며_숫자가_아니면_계약된_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/artists").param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE"));
        mockMvc.perform(get("/api/artists").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_SIZE"));
        mockMvc.perform(get("/api/artists")
                        .param("page", "abc")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE"));
        mockMvc.perform(get("/api/artists")
                        .param("page", "0")
                        .param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_SIZE"));
    }

    @Test
    void 검색_장르_정렬_페이지_파라미터를_서비스에_전달한다() throws Exception {
        given(artistService.findAll(2, 5, "BAND", "NAME", "잔나비"))
                .willReturn(new PageResponse<>(List.of(), 2, 5, 0, 0, false, true));

        mockMvc.perform(get("/api/artists")
                        .param("page", "2")
                        .param("size", "5")
                        .param("genre", "BAND")
                        .param("sort", "NAME")
                        .param("q", "잔나비"))
                .andExpect(status().isOk());

        verify(artistService).findAll(2, 5, "BAND", "NAME", "잔나비");
    }

    @Test
    void 상세는_명세의_최상위_필드를_반환한다() throws Exception {
        ArtistDetailResponse response = new ArtistDetailResponse(
                1L, "BTS", List.of("방탄소년단"), ArtistGenre.DANCE, null,
                "https://instagram.com/bts",
                new ArtistSectionResponse<>(List.of(), 0),
                new ArtistSectionResponse<>(List.of(), 0));
        given(artistService.findById(1L)).willReturn(response);

        mockMvc.perform(get("/api/artists/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.otherNames[0]").value("방탄소년단"))
                .andExpect(jsonPath("$.upcomingShows.total").value(0))
                .andExpect(jsonPath("$.appearances.total").value(0));
    }

    @Test
    void 없는_아티스트는_공통_404_응답을_반환한다() throws Exception {
        given(artistService.findById(99L))
                .willThrow(new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));

        mockMvc.perform(get("/api/artists/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ARTIST_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/artists/99"));
    }

    @Test
    void 숫자가_아닌_id는_INVALID_PATH_VARIABLE_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/artists/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PATH_VARIABLE"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/artists/abc"));
    }

    @Test
    void 잘못된_목록_파라미터는_공통_오류_형식으로_반환한다() throws Exception {
        given(artistService.findAll(-1, 10, null, "APPEARANCES", null))
                .willThrow(new FestaException(CommonErrorCode.INVALID_PAGE));
        given(artistService.findAll(0, 51, null, "APPEARANCES", null))
                .willThrow(new FestaException(CommonErrorCode.INVALID_PAGE_SIZE));
        given(artistService.findAll(0, 10, "ROCK", "APPEARANCES", null))
                .willThrow(new FestaException(ArtistErrorCode.ARTIST_INVALID_GENRE_TYPE));
        given(artistService.findAll(0, 10, null, "RECENT", null))
                .willThrow(new FestaException(ArtistErrorCode.ARTIST_INVALID_SORT_TYPE));
        String longQuery = "가".repeat(51);
        given(artistService.findAll(0, 10, null, "APPEARANCES", longQuery))
                .willThrow(new FestaException(ArtistErrorCode.ARTIST_INVALID_QUERY));

        assertListError("page", "-1", "size", "10", "INVALID_PAGE");
        assertListError("page", "0", "size", "51", "INVALID_PAGE_SIZE");
        assertListError("page", "0", "size", "10", "genre", "ROCK", "ARTIST_INVALID_GENRE_TYPE");
        assertListError("page", "0", "size", "10", "sort", "RECENT", "ARTIST_INVALID_SORT_TYPE");
        assertListError("page", "0", "size", "10", "q", longQuery, "ARTIST_INVALID_QUERY");
    }

    private void assertListError(String name1, String value1, String name2, String value2,
                                 String errorCode) throws Exception {
        mockMvc.perform(get("/api/artists").param(name1, value1).param(name2, value2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    private void assertListError(String name1, String value1, String name2, String value2,
                                 String name3, String value3, String errorCode) throws Exception {
        mockMvc.perform(get("/api/artists")
                        .param(name1, value1)
                        .param(name2, value2)
                        .param(name3, value3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(errorCode));
    }
}
