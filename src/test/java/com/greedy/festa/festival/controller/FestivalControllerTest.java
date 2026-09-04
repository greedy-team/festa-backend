package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalListItemResponse;
import com.greedy.festa.festival.dto.FestivalRecentResponse;
import com.greedy.festa.festival.dto.FestivalSortType;
import com.greedy.festa.festival.dto.FestivalUpcomingResponse;
import com.greedy.festa.festival.dto.HostSummaryResponse;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.service.FestivalService;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class FestivalControllerTest {

    @Test
    void listQueryLongerThanFiftyCharactersReturnsFestivalInvalidQuery() throws Exception {
        String query = "가".repeat(51);
        given(festivalService.getFestivals(null, null, null, null, query,
                FestivalSortType.LATEST, 0, 20))
                .willThrow(new FestaException(FestivalErrorCode.FESTIVAL_INVALID_QUERY));

        mockMvc.perform(get("/api/festivals").param("q", query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_INVALID_QUERY"))
                .andExpect(jsonPath("$.instance").value("/api/festivals"));
    }

    private FestivalService festivalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        festivalService = mock(FestivalService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FestivalController(festivalService, new GlobalExceptionHandler()))
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

    @Test
    void 목록의_year가_숫자가_아니면_400_FESTIVAL_INVALID_FILTER이다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/festivals").param("year", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_INVALID_FILTER"))
                .andExpect(jsonPath("$.instance").value("/api/festivals"));
    }

    @Test
    void 목록의_page가_숫자가_아니면_400_INVALID_PAGE이다() throws Exception {
        // given - 페이지네이션은 공통 코드를 재사용한다 (#46)
        // when & then
        mockMvc.perform(get("/api/festivals").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE"));
    }

    @Test
    void 목록의_size가_숫자가_아니면_400_INVALID_PAGE_SIZE이다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/festivals").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_SIZE"));
    }

    @Test
    void 목록의_status가_어휘_밖이면_400_FESTIVAL_INVALID_STATUS_TYPE이다() throws Exception {
        // given - 파싱은 컨트롤러의 FestivalStatus.from이 한다. 이 컨트롤러의 로컬
        //         @ExceptionHandler는 타입 불일치만 잡으므로 FestaException은 전역 핸들러가 받는다
        // when & then
        mockMvc.perform(get("/api/festivals").param("status", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_INVALID_STATUS_TYPE"))
                .andExpect(jsonPath("$.instance").value("/api/festivals"));
    }

    @Test
    void 목록의_status가_빈_값이면_필터를_주지_않은_것과_같다() throws Exception {
        // given - from("")이 던지지 않고 null을 돌려줘야 서비스가 status 없이 불린다
        given(festivalService.getFestivals(null, null, null, null, null, FestivalSortType.LATEST, 0, 20))
                .willReturn(PageResponse.from(new PageImpl<>(List.of(목록_항목()),
                        PageRequest.of(0, 20), 1)));

        // when & then
        mockMvc.perform(get("/api/festivals").param("status", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("대동제"));
    }

    @Test
    void 목록의_sort는_대소문자와_앞뒤_공백을_가리지_않는다() throws Exception {
        // given - 파싱이 EnumParser로 옮겨가며 정규화가 붙었다
        given(festivalService.getFestivals(null, null, null, null, null,
                FestivalSortType.UPCOMING, 0, 20))
                .willReturn(PageResponse.from(new PageImpl<>(List.of(목록_항목()),
                        PageRequest.of(0, 20), 1)));

        // when & then
        mockMvc.perform(get("/api/festivals").param("sort", "  upcoming  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("대동제"));
    }

    @Test
    void 목록의_sort가_어휘_밖이면_400_FESTIVAL_INVALID_SORT_TYPE이다() throws Exception {
        // given - 기본값이 있어도 틀린 값은 기본값으로 흘리지 않는다
        // when & then
        mockMvc.perform(get("/api/festivals").param("sort", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_INVALID_SORT_TYPE"))
                .andExpect(jsonPath("$.instance").value("/api/festivals"));
    }

    @Test
    void 목록의_q는_컨트롤러가_손대지_않고_서비스로_넘긴다() throws Exception {
        // given - 공백 정리와 LIKE 이스케이프는 서비스의 몫이다
        given(festivalService.getFestivals(null, null, null, null, "  대동  ",
                FestivalSortType.LATEST, 0, 20))
                .willReturn(PageResponse.from(new PageImpl<>(List.of(목록_항목()),
                        PageRequest.of(0, 20), 1)));

        // when & then
        mockMvc.perform(get("/api/festivals").param("q", "  대동  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("대동제"));
    }

    @Test
    void 목록_응답에는_venueName이_없다() throws Exception {
        // given
        given(festivalService.getFestivals(null, null, null, null, null, FestivalSortType.LATEST, 0, 20))
                .willReturn(PageResponse.from(new PageImpl<>(List.of(목록_항목()),
                        PageRequest.of(0, 20), 1)));

        // when & then
        mockMvc.perform(get("/api/festivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("대동제"))
                .andExpect(jsonPath("$.items[0].host.name").value("성균관대학교"))
                .andExpect(jsonPath("$.items[0].venueName").doesNotExist());
    }

    @Test
    void 이_컨트롤러가_모르는_파라미터는_전역_핸들러가_판정한다() throws Exception {
        // given - #83이 GET /api/festivals/{id}를 더하면 경로 변수가 이 컨트롤러로 들어온다.
        // 이름을 안 가리고 FESTIVAL_INVALID_LIMIT을 내면 HostController·ArtistController와 어긋난다
        Method 시그니처 = getClass().getDeclaredMethod("경로변수_시그니처", Long.class);
        MethodArgumentTypeMismatchException 예외 = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", new MethodParameter(시그니처, 0), null);

        // when
        ResponseEntity<ErrorResponse> 응답 =
                new FestivalController(festivalService, new GlobalExceptionHandler())
                        .handleQueryParamTypeMismatch(
                                예외, new MockHttpServletRequest("GET", "/api/festivals/abc"));

        // then
        assertThat(응답.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(응답.getBody()).isNotNull();
        assertThat(응답.getBody().errorCode()).isEqualTo("INVALID_PATH_VARIABLE");
    }

    @SuppressWarnings("unused")
    private void 경로변수_시그니처(@PathVariable Long id) {
    }

    private FestivalListItemResponse 목록_항목() {
        return new FestivalListItemResponse(
                1L, "대동제", 주최(), "https://cdn.example.com/poster.jpg",
                LocalDate.of(2026, 5, 30), LocalDate.of(2026, 6, 1));
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
