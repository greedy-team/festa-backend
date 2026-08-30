package com.greedy.festa.artist.controller;

import com.greedy.festa.artist.dto.ArtistSortType;
import com.greedy.festa.artist.service.ArtistAdminService;
import com.greedy.festa.artist.service.ArtistMergeCandidateService;
import com.greedy.festa.artist.service.ArtistMergeService;
import com.greedy.festa.global.dto.PageResponse;
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

/**
 * genre와 sort의 파싱이 이 컨트롤러에 있다. 어휘 밖의 값에 어떤 에러 코드가 나가고
 * 값을 생략했을 때 어떤 기본값이 서비스로 가는지는 여기서만 정해지므로 여기서 못박는다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ArtistAdminControllerTest {

    private ArtistAdminService artistAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        artistAdminService = mock(ArtistAdminService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ArtistAdminController(
                        artistAdminService,
                        mock(ArtistMergeService.class),
                        mock(ArtistMergeCandidateService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 목록의_genre를_생략하면_필터가_없고_sort는_CREATED_DESC다() throws Exception {
        // given - genre는 기본값 없는 필터, sort는 기본값 있는 정렬이다
        given(artistAdminService.findAll(null, null, null, ArtistSortType.CREATED_DESC, 0, 20))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false, false));

        // when & then
        mockMvc.perform(get("/api/admin/artists"))
                .andExpect(status().isOk());

        verify(artistAdminService).findAll(null, null, null, ArtistSortType.CREATED_DESC, 0, 20);
    }

    @Test
    void 목록의_genre가_어휘_밖이면_400_ARTIST_INVALID_GENRE_TYPE이다() throws Exception {
        mockMvc.perform(get("/api/admin/artists").param("genre", "ROCK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ARTIST_INVALID_GENRE_TYPE"));
    }

    @Test
    void 목록의_sort가_어휘_밖이면_400_ARTIST_INVALID_SORT_TYPE이다() throws Exception {
        mockMvc.perform(get("/api/admin/artists").param("sort", "RECENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ARTIST_INVALID_SORT_TYPE"));
    }
}
