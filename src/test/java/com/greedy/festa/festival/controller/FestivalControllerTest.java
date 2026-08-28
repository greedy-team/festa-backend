package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalRecentResponse;
import com.greedy.festa.festival.dto.FestivalUpcomingResponse;
import com.greedy.festa.festival.dto.HostSummaryResponse;
import com.greedy.festa.festival.service.FestivalService;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class FestivalControllerTest {

    private FestivalService festivalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        festivalService = mock(FestivalService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FestivalController(festivalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upcoming의_limit이_숫자가_아니면_400_FESTIVAL_INVALID_LIMIT이다() throws Exception {
        // given - int 바인딩은 서비스 검증 전에 터진다. 잡지 않으면 전역 Exception 핸들러가 500으로 삼킨다
        // when & then
        mockMvc.perform(get("/api/festivals/upcoming").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_INVALID_LIMIT"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/festivals/upcoming"));
    }

    @Test
    void recent의_limit이_숫자가_아니면_400_FESTIVAL_INVALID_LIMIT이다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/festivals/recent").param("limit", "3.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_INVALID_LIMIT"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/festivals/recent"));
    }

    @Test
    void upcoming_응답에는_venueName이_실린다() throws Exception {
        // given - 홈 히어로가 장소를 바로 보여준다 (festa-frontend UpcomingFestival)
        given(festivalService.getUpcomingFestivals(10)).willReturn(List.of(다가오는_축제()));

        // when & then
        mockMvc.perform(get("/api/festivals/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].venueName").value("성균관대학교 인문사회과학캠퍼스"));
    }

    @Test
    void recent_응답에는_venueName이_없다() throws Exception {
        // given - 프론트가 RecentFestival에서 일부러 뺀 필드다. 카드가 장소를 쓰지 않는다
        given(festivalService.getRecentPublished(10)).willReturn(List.of(최근_축제()));

        // when & then
        mockMvc.perform(get("/api/festivals/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("대동제"))
                .andExpect(jsonPath("$.items[0].venueName").doesNotExist());
    }

    private FestivalUpcomingResponse 다가오는_축제() {
        return new FestivalUpcomingResponse(
                1L, "대동제", 주최(), "https://cdn.example.com/poster.jpg",
                "성균관대학교 인문사회과학캠퍼스",
                LocalDate.of(2026, 5, 30), LocalDate.of(2026, 6, 1));
    }

    private FestivalRecentResponse 최근_축제() {
        return new FestivalRecentResponse(
                1L, "대동제", 주최(), "https://cdn.example.com/poster.jpg",
                LocalDate.of(2026, 5, 30), LocalDate.of(2026, 6, 1));
    }

    private HostSummaryResponse 주최() {
        return new HostSummaryResponse(10L, "성균관대학교", "https://cdn.example.com/logo.png");
    }
}
