package com.greedy.festa.artist.controller;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.exception.LineupErrorCode;
import com.greedy.festa.artist.service.ArtistAdminService;
import com.greedy.festa.artist.service.ArtistMergeCandidateService;
import com.greedy.festa.artist.service.ArtistMergeService;
import com.greedy.festa.artist.service.LineupAdminService;
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

class AdminSingleLookupControllerTest {

    @Test
    void artistSingleLookupDelegatesToService() throws Exception {
        ArtistAdminService service = mock(ArtistAdminService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ArtistAdminController(
                service, mock(ArtistMergeService.class), mock(ArtistMergeCandidateService.class))).build();

        mvc.perform(get("/api/admin/artists/1")).andExpect(status().isOk());

        verify(service).findOne(1L);
    }

    @Test
    void missingArtistReturns404Contract() throws Exception {
        ArtistAdminService service = mock(ArtistAdminService.class);
        given(service.findOne(1L)).willThrow(new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ArtistAdminController(
                        service, mock(ArtistMergeService.class), mock(ArtistMergeCandidateService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/admin/artists/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ARTIST_NOT_FOUND"));
    }

    @Test
    void lineupSingleLookupDelegatesToService() throws Exception {
        LineupAdminService service = mock(LineupAdminService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LineupAdminController(service)).build();

        mvc.perform(get("/api/admin/lineups/1")).andExpect(status().isOk());

        verify(service).findOne(1L);
    }

    @Test
    void missingLineupReturns404Contract() throws Exception {
        LineupAdminService service = mock(LineupAdminService.class);
        given(service.findOne(1L)).willThrow(new FestaException(LineupErrorCode.LINEUP_NOT_FOUND));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LineupAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/admin/lineups/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LINEUP_NOT_FOUND"));
    }
}
