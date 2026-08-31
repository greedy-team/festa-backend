package com.greedy.festa.lineup.controller;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import com.greedy.festa.lineup.exception.LineupErrorCode;
import com.greedy.festa.lineup.service.LineupAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LineupAdminControllerTest {

    @Test
    void 단건_조회는_서비스에_위임한다() throws Exception {
        LineupAdminService service = mock(LineupAdminService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LineupAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/admin/festivals/1/lineups/2")).andExpect(status().isOk());

        verify(service).findOne(1L, 2L);
    }

    @Test
    void 없는_라인업을_단건_조회하면_404_계약을_따른다() throws Exception {
        LineupAdminService service = mock(LineupAdminService.class);
        given(service.findOne(1L, 2L)).willThrow(new FestaException(LineupErrorCode.LINEUP_NOT_FOUND));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LineupAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/admin/festivals/1/lineups/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LINEUP_NOT_FOUND"));
    }
}
