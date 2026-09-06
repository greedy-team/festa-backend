package com.greedy.festa.lineup.controller;

import com.greedy.festa.lineup.dto.LineupCreateRequest;
import com.greedy.festa.lineup.dto.LineupResponse;
import com.greedy.festa.lineup.dto.LineupUpdateRequest;
import com.greedy.festa.lineup.service.LineupAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(controllers = LineupAdminController.class,
        excludeAutoConfiguration = OAuth2ClientWebSecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("NonAsciiCharacters")
class LineupAdminWriteContractTest {

    private static final String 목록_경로 = "/api/admin/festivals/{festivalId}/lineups";
    private static final String 단건_경로 = "/api/admin/festivals/{festivalId}/lineups/{lineupId}";
    private static final long 축제_id = 11L;
    private static final long 라인업_id = 99L;
    private static final long 아티스트_id = 5L;

    private static final LineupResponse 라인업_응답 = new LineupResponse(
            라인업_id, 축제_id, "그리디 페스타", 아티스트_id, "실리카겔", 2, 3);

    private static final LineupResponse 시크릿_게스트_응답 = new LineupResponse(
            라인업_id, 축제_id, "그리디 페스타", null, null, 2, 3);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private LineupAdminService lineupAdminService;

    @Test
    void 등록은_201과_LineupResponse_7필드를_반환한다() {
        // given
        given(lineupAdminService.create(anyLong(), any())).willReturn(라인업_응답);

        // when
        MvcTestResult 결과 = mvc.post().uri(목록_경로, 축제_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"artistId":5,"day":2,"displayOrder":3}
                        """)
                .exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("lineupId", "festivalId", "festivalName", "artistId",
                        "artistName", "day", "displayOrder");
    }

    @Test
    void 등록은_경로의_festivalId와_LineupCreateRequest_3필드를_서비스로_넘긴다() {
        // given
        given(lineupAdminService.create(anyLong(), any())).willReturn(라인업_응답);

        // when
        mvc.post().uri(목록_경로, 축제_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"artistId":5,"day":2,"displayOrder":3}
                        """)
                .exchange();

        // then
        ArgumentCaptor<LineupCreateRequest> 캡처 = ArgumentCaptor.forClass(LineupCreateRequest.class);
        then(lineupAdminService).should().create(eq(축제_id), 캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new LineupCreateRequest(아티스트_id, 2, 3));
    }

    @Test
    void 등록에서_artistId_생략은_null_시크릿_게스트로_바인딩된다() {
        // given
        given(lineupAdminService.create(anyLong(), any())).willReturn(시크릿_게스트_응답);

        // when
        mvc.post().uri(목록_경로, 축제_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"day":2,"displayOrder":3}
                        """)
                .exchange();

        // then
        ArgumentCaptor<LineupCreateRequest> 캡처 = ArgumentCaptor.forClass(LineupCreateRequest.class);
        then(lineupAdminService).should().create(eq(축제_id), 캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new LineupCreateRequest(null, 2, 3));
    }

    @Test
    void 시크릿_게스트_응답도_artistId와_artistName_키를_null로_유지한다() {
        // given
        given(lineupAdminService.create(anyLong(), any())).willReturn(시크릿_게스트_응답);

        // when
        MvcTestResult 결과 = mvc.post().uri(목록_경로, 축제_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"day":2,"displayOrder":3}
                        """)
                .exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("lineupId", "festivalId", "festivalName", "artistId",
                        "artistName", "day", "displayOrder")
                .containsEntry("artistId", null)
                .containsEntry("artistName", null);
    }

    @Test
    void 수정은_PATCH로_200과_LineupResponse_7필드를_반환한다() {
        // given
        given(lineupAdminService.update(anyLong(), anyLong(), any())).willReturn(라인업_응답);

        // when
        MvcTestResult 결과 = mvc.patch().uri(단건_경로, 축제_id, 라인업_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"artistId":5,"day":2,"displayOrder":3}
                        """)
                .exchange();

        // then
        assertThat(결과).hasStatusOk()
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("lineupId", "festivalId", "festivalName", "artistId",
                        "artistName", "day", "displayOrder");
    }

    @Test
    void 수정은_festivalId와_lineupId를_뒤바꾸지_않고_순서대로_넘긴다() {
        // given
        given(lineupAdminService.update(anyLong(), anyLong(), any())).willReturn(라인업_응답);

        // when
        mvc.patch().uri(단건_경로, 축제_id, 라인업_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"artistId":5,"day":2,"displayOrder":3}
                        """)
                .exchange();

        // then
        ArgumentCaptor<LineupUpdateRequest> 캡처 = ArgumentCaptor.forClass(LineupUpdateRequest.class);
        then(lineupAdminService).should().update(eq(축제_id), eq(라인업_id), 캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new LineupUpdateRequest(아티스트_id, 2, 3));
    }

    @Test
    void 수정에서_artistId_명시적_null은_시크릿_게스트로_바인딩된다() {
        // given
        given(lineupAdminService.update(anyLong(), anyLong(), any())).willReturn(시크릿_게스트_응답);

        // when
        mvc.patch().uri(단건_경로, 축제_id, 라인업_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"artistId":null,"day":2,"displayOrder":3}
                        """)
                .exchange();

        // then
        ArgumentCaptor<LineupUpdateRequest> 캡처 = ArgumentCaptor.forClass(LineupUpdateRequest.class);
        then(lineupAdminService).should().update(eq(축제_id), eq(라인업_id), 캡처.capture());
        assertThat(캡처.getValue().artistId()).isNull();
    }

    @Test
    void 삭제는_204와_빈_본문을_반환하고_festivalId와_lineupId를_순서대로_넘긴다() {
        // when
        MvcTestResult 결과 = mvc.delete().uri(단건_경로, 축제_id, 라인업_id).exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.NO_CONTENT)
                .bodyText().isEmpty();
        then(lineupAdminService).should().delete(축제_id, 라인업_id);
    }
}
