package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalCoverageItem;
import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.dto.FestivalCoverageStatus;
import com.greedy.festa.festival.dto.FestivalCoverageSummary;
import com.greedy.festa.festival.service.FestivalCoverageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FestivalCoverageAdminControllerTest {

    private FestivalCoverageService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(FestivalCoverageService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FestivalCoverageAdminController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void coverage_api는_summary_items와_page_정보를_반환한다() throws Exception {
        FestivalCoverageResponse response = new FestivalCoverageResponse(
                2026,
                new FestivalCoverageSummary(29, 18, 4, 7, 76),
                List.of(new FestivalCoverageItem(
                        1L,
                        10L,
                        "성균관대학교",
                        "대동제 2026",
                        LocalDate.of(2026, 5, 30),
                        LocalDate.of(2026, 6, 1),
                        FestivalCoverageStatus.REVIEW_PENDING,
                        "https://instagram.com/skku"
                )),
                0, 10, 11, 2, true, false
        );
        given(service.findCoverage(
                org.mockito.ArgumentMatchers.eq(2026),
                org.mockito.ArgumentMatchers.eq("REVIEW_PENDING"),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(response);

        mockMvc.perform(get("/admin/festivals/coverage")
                        .param("year", "2026")
                        .param("status", "REVIEW_PENDING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.summary.totalHosts").value(29))
                .andExpect(jsonPath("$.summary.published").value(18))
                .andExpect(jsonPath("$.summary.reviewPending").value(4))
                .andExpect(jsonPath("$.summary.needsCheck").value(7))
                .andExpect(jsonPath("$.summary.coverageRate").value(76))
                .andExpect(jsonPath("$.items[0].hostId").value(1))
                .andExpect(jsonPath("$.items[0].festivalId").value(10))
                .andExpect(jsonPath("$.items[0].status").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(service).findCoverage(
                org.mockito.ArgumentMatchers.eq(2026),
                org.mockito.ArgumentMatchers.eq("REVIEW_PENDING"),
                argThat(allOf(
                        hasProperty("pageNumber", is(0)),
                        hasProperty("pageSize", is(10))
                ))
        );
    }
}
