package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalCoverageItem;
import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.dto.FestivalCoverageStatus;
import com.greedy.festa.festival.dto.FestivalCoverageSummary;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.service.FestivalPublishService;
import com.greedy.festa.festival.service.FestivalAdminService;
import com.greedy.festa.festival.service.FestivalCoverageService;
import com.greedy.festa.festival.dto.FestivalSortType;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FestivalAdminControllerTest {

    private FestivalAdminService adminService;
    private FestivalPublishService publishService;
    private FestivalCoverageService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminService = mock(FestivalAdminService.class);
        publishService = mock(FestivalPublishService.class);
        service = mock(FestivalCoverageService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FestivalAdminController(adminService, publishService, service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 단건_조회는_서비스에_위임한다() throws Exception {
        mockMvc.perform(get("/api/admin/festivals/1")).andExpect(status().isOk());

        verify(adminService).findOne(1L);
    }

    @Test
    void 없는_축제를_단건_조회하면_404_계약을_따른다() throws Exception {
        given(adminService.findOne(1L)).willThrow(new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));

        mockMvc.perform(get("/api/admin/festivals/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"externalVisitor", "verification", "ticketType"})
    void 등록의_UNKNOWN_입력은_400_INVALID_REQUEST_BODY다(String field) throws Exception {
        given(adminService.create(any()))
                .willThrow(new FestaException(CommonErrorCode.INVALID_REQUEST_BODY));

        mockMvc.perform(post("/api/admin/festivals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"externalVisitor", "verification", "ticketType"})
    void 수정의_UNKNOWN_입력은_400_INVALID_REQUEST_BODY다(String field) throws Exception {
        given(adminService.update(any(), any()))
                .willThrow(new FestaException(CommonErrorCode.INVALID_REQUEST_BODY));

        mockMvc.perform(patch("/api/admin/festivals/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void coverage_api는_summary와_hosts_page를_반환한다() throws Exception {
        FestivalCoverageItem item = new FestivalCoverageItem(
                1L, 10L, "성균관대학교", "대동제 2026",
                LocalDate.of(2026, 5, 30), LocalDate.of(2026, 6, 1),
                FestivalCoverageStatus.REVIEW_PENDING, "https://instagram.com/skku");
        FestivalCoverageResponse response = new FestivalCoverageResponse(
                2026,
                new FestivalCoverageSummary(29, 18, 4, 7, 76),
                new PageResponse<>(List.of(item), 0, 10, 11, 2, true, false));
        given(service.findCoverage(2026, "REVIEW_PENDING", 0, 10)).willReturn(response);

        mockMvc.perform(get("/api/admin/festivals/coverage")
                        .param("year", "2026")
                        .param("status", "REVIEW_PENDING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.summary.totalHosts").value(29))
                .andExpect(jsonPath("$.summary.coverageRate").value(76))
                .andExpect(jsonPath("$.hosts.items[0].hostId").value(1))
                .andExpect(jsonPath("$.hosts.items[0].festivalId").value(10))
                .andExpect(jsonPath("$.hosts.items[0].status").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.hosts.page").value(0))
                .andExpect(jsonPath("$.hosts.size").value(10))
                .andExpect(jsonPath("$.hosts.totalElements").value(11))
                .andExpect(jsonPath("$.hosts.totalPages").value(2))
                .andExpect(jsonPath("$.hosts.hasNext").value(true))
                .andExpect(jsonPath("$.hosts.hasPrevious").value(false));

        verify(service).findCoverage(2026, "REVIEW_PENDING", 0, 10);
    }

    @Test
    void page와_size를_생략하면_0과_20을_사용하고_sort는_무시한다() throws Exception {
        FestivalCoverageResponse response = new FestivalCoverageResponse(
                2026,
                new FestivalCoverageSummary(0, 0, 0, 0, 0),
                new PageResponse<>(List.of(), 0, 20, 0, 0, false, false));
        given(service.findCoverage(2026, null, 0, 20)).willReturn(response);

        mockMvc.perform(get("/api/admin/festivals/coverage")
                        .param("year", "2026")
                        .param("sort", "hostName,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosts.page").value(0))
                .andExpect(jsonPath("$.hosts.size").value(20));

        verify(service).findCoverage(2026, null, 0, 20);
    }

    @Test
    void published_status를_조회할_수_있다() throws Exception {
        FestivalCoverageItem item = new FestivalCoverageItem(
                1L, null, "Published Host", null, null, null,
                FestivalCoverageStatus.PUBLISHED, null);
        FestivalCoverageResponse response = new FestivalCoverageResponse(
                2026,
                new FestivalCoverageSummary(1, 1, 0, 0, 100),
                new PageResponse<>(List.of(item), 0, 20, 1, 1, false, false));
        given(service.findCoverage(2026, "PUBLISHED", 0, 20)).willReturn(response);

        mockMvc.perform(get("/api/admin/festivals/coverage")
                        .param("year", "2026")
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosts.items[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.summary.coverageRate").value(100));
    }

    @Test
    void 검수_목록의_sort를_생략하면_IMPORTED_DESC로_조회한다() throws Exception {
        // given - 기본값은 EnumParser 호출부가 고른다. 여기가 유일한 지정 지점이다
        given(publishService.findAll(null, null, null, null, null,
                FestivalSortType.IMPORTED_DESC, 0, 20))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false, false));

        // when & then
        mockMvc.perform(get("/api/admin/festivals"))
                .andExpect(status().isOk());

        verify(publishService).findAll(null, null, null, null, null,
                FestivalSortType.IMPORTED_DESC, 0, 20);
    }

    @Test
    void 검수_목록의_sort가_어휘_밖이면_400_FESTIVAL_INVALID_SORT_TYPE이다() throws Exception {
        // given - Spring Pageable 형식(hostName,desc)은 이 API의 어휘가 아니다
        // when & then
        mockMvc.perform(get("/api/admin/festivals").param("sort", "hostName,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_INVALID_SORT_TYPE"));
    }

    @Test
    void 허용_범위를_벗어난_year는_기존_Festival_ErrorCode로_400을_반환한다() throws Exception {
        given(service.findCoverage(2025, null, 0, 20))
                .willThrow(new FestaException(FestivalErrorCode.FESTIVAL_COVERAGE_INVALID_YEAR));

        mockMvc.perform(get("/api/admin/festivals/coverage").param("year", "2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_COVERAGE_INVALID_YEAR"));
    }

    @Test
    void 잘못된_page와_size는_공통_400을_반환한다() throws Exception {
        given(service.findCoverage(2026, null, -1, 20))
                .willThrow(new FestaException(CommonErrorCode.INVALID_PAGE));
        given(service.findCoverage(2026, null, 0, 51))
                .willThrow(new FestaException(CommonErrorCode.INVALID_PAGE_SIZE));

        mockMvc.perform(get("/api/admin/festivals/coverage")
                        .param("year", "2026").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE"));
        mockMvc.perform(get("/api/admin/festivals/coverage")
                        .param("year", "2026").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_SIZE"));
    }
}
