package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.service.FestivalAdminService;
import com.greedy.festa.festival.service.FestivalCoverageService;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FestivalAdminSingleLookupControllerTest {

    @Test
    void singleLookupDelegatesToService() throws Exception {
        FestivalAdminService service = mock(FestivalAdminService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new FestivalAdminController(service, mock(FestivalCoverageService.class))).build();

        mvc.perform(get("/api/admin/festivals/1")).andExpect(status().isOk());

        verify(service).findOne(1L);
    }

    @Test
    void missingFestivalReturns404Contract() throws Exception {
        FestivalAdminService service = mock(FestivalAdminService.class);
        given(service.findOne(1L)).willThrow(new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new FestivalAdminController(service, mock(FestivalCoverageService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/admin/festivals/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_NOT_FOUND"));
    }
}
