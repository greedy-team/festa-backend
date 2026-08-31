package com.greedy.festa.host.controller;

import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.service.HostAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class HostAdminControllerTest {

    private HostAdminService hostAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        hostAdminService = mock(HostAdminService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HostAdminController(hostAdminService))
                .build();
    }

    @Test
    void page와_size를_생략하면_0과_20으로_조회한다() throws Exception {
        given(hostAdminService.findAll(0, 20)).willReturn(비어_있는_페이지(0, 20));

        mockMvc.perform(get("/api/admin/hosts"))
                .andExpect(status().isOk());

        verify(hostAdminService).findAll(0, 20);
    }

    @Test
    void 받은_page와_size를_그대로_넘긴다() throws Exception {
        given(hostAdminService.findAll(2, 50)).willReturn(비어_있는_페이지(2, 50));

        mockMvc.perform(get("/api/admin/hosts")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(hostAdminService).findAll(2, 50);
    }

    private PageResponse<HostResponse> 비어_있는_페이지(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, false, false);
    }
}
