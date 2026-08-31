package com.greedy.festa.host.controller;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.service.HostAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HostAdminSingleLookupControllerTest {

    @Test
    void singleLookupDelegatesToService() throws Exception {
        HostAdminService service = mock(HostAdminService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HostAdminController(service)).build();

        mvc.perform(get("/api/admin/hosts/1")).andExpect(status().isOk());

        verify(service).findOne(1L);
    }

    @Test
    void missingHostReturns404Contract() throws Exception {
        HostAdminService service = mock(HostAdminService.class);
        given(service.findOne(1L)).willThrow(new FestaException(HostErrorCode.HOST_NOT_FOUND));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HostAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/admin/hosts/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HOST_NOT_FOUND"));
    }
}
