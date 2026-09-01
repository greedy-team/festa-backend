package com.greedy.festa.host.controller;

import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.service.HostAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test
    void 관리자_HostResponse는_모든_반환_지점에서_hostId만_노출한다() throws Exception {
        HostResponse response = 주최_응답();
        given(hostAdminService.findAll(0, 20))
                .willReturn(new PageResponse<>(List.of(response), 0, 20, 1, 1, false, false));
        given(hostAdminService.findOne(1L)).willReturn(response);
        given(hostAdminService.create(org.mockito.ArgumentMatchers.any())).willReturn(response);
        given(hostAdminService.update(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .willReturn(response);

        mockMvc.perform(get("/api/admin/hosts"))
                .andExpect(jsonPath("$.items[0].hostId").value(1))
                .andExpect(jsonPath("$.items[0].id").doesNotExist());
        mockMvc.perform(get("/api/admin/hosts/1"))
                .andExpect(jsonPath("$.hostId").value(1))
                .andExpect(jsonPath("$.id").doesNotExist());
        mockMvc.perform(post("/api/admin/hosts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.hostId").value(1))
                .andExpect(jsonPath("$.id").doesNotExist());
        mockMvc.perform(patch("/api/admin/hosts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.hostId").value(1))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    private PageResponse<HostResponse> 비어_있는_페이지(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, false, false);
    }

    private HostResponse 주최_응답() {
        return new HostResponse(
                1L, "테스트대학교", "테스트대", "서울", null, null, null, null, 0L);
    }
}
