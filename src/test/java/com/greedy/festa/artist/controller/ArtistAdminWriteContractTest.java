package com.greedy.festa.artist.controller;

import com.greedy.festa.artist.dto.ArtistCreateRequest;
import com.greedy.festa.artist.dto.ArtistMergeRequest;
import com.greedy.festa.artist.dto.ArtistMergeResponse;
import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.ArtistUpdateRequest;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.service.ArtistAdminService;
import com.greedy.festa.artist.service.ArtistMergeCandidateService;
import com.greedy.festa.artist.service.ArtistMergeService;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@WebMvcTest(controllers = ArtistAdminController.class,
        excludeAutoConfiguration = OAuth2ClientWebSecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("NonAsciiCharacters")
class ArtistAdminWriteContractTest {

    private static final String 목록_경로 = "/api/admin/artists";
    private static final String 단건_경로 = "/api/admin/artists/{id}";
    private static final String 병합_경로 = "/api/admin/artists/merge";
    private static final long 아티스트_id = 7L;

    private static final ArtistResponse 아티스트_응답 = new ArtistResponse(
            아티스트_id, "실리카겔", List.of("Silica Gel"), ArtistGenre.BAND,
            "https://image.example/silicagel.png", "https://instagram.com/silicagel", 3,
            false, Instant.parse("2026-01-02T03:04:05Z"));

    private static final ArtistMergeResponse 병합_응답 = new ArtistMergeResponse(
            아티스트_id, "실리카겔", 2, 5, 1, List.of("Silica Gel"), false);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ArtistAdminService artistAdminService;

    @MockitoBean
    private ArtistMergeService artistMergeService;

    @MockitoBean
    private ArtistMergeCandidateService artistMergeCandidateService;

    @Test
    void 등록은_201과_ArtistResponse_9필드를_반환한다() {
        // given
        given(artistAdminService.create(any())).willReturn(아티스트_응답);

        // when
        MvcTestResult 결과 = mvc.post().uri(목록_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"실리카겔"}
                        """)
                .exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("artistId", "name", "otherNames", "genre", "imageUrl",
                        "instagramUrl", "appearanceCount", "needsReview", "createdAt");
    }

    @Test
    void 등록_본문은_ArtistCreateRequest_4필드로_바인딩된다() {
        // given
        given(artistAdminService.create(any())).willReturn(아티스트_응답);

        // when
        mvc.post().uri(목록_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"실리카겔","otherNames":["Silica Gel"],"genre":"BAND","instagramUrl":"https://instagram.com/silicagel"}
                        """)
                .exchange();

        // then
        ArgumentCaptor<ArtistCreateRequest> 캡처 = ArgumentCaptor.forClass(ArtistCreateRequest.class);
        then(artistAdminService).should().create(캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new ArtistCreateRequest(
                "실리카겔", List.of("Silica Gel"), ArtistGenre.BAND,
                "https://instagram.com/silicagel"));
    }

    @Test
    void 수정은_PATCH로_200과_ArtistResponse_9필드를_반환한다() {
        // given
        given(artistAdminService.update(anyLong(), any())).willReturn(아티스트_응답);

        // when
        MvcTestResult 결과 = mvc.patch().uri(단건_경로, 아티스트_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"실리카겔"}
                        """)
                .exchange();

        // then
        assertThat(결과).hasStatusOk()
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("artistId", "name", "otherNames", "genre", "imageUrl",
                        "instagramUrl", "appearanceCount", "needsReview", "createdAt");
    }

    @Test
    void 수정은_경로의_id와_ArtistUpdateRequest_5필드를_서비스로_넘긴다() {
        // given
        given(artistAdminService.update(anyLong(), any())).willReturn(아티스트_응답);

        // when
        mvc.patch().uri(단건_경로, 아티스트_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"실리카겔","otherNames":["Silica Gel"],"genre":"BAND","instagramUrl":"https://instagram.com/silicagel","needsReview":true}
                        """)
                .exchange();

        // then
        ArgumentCaptor<ArtistUpdateRequest> 캡처 = ArgumentCaptor.forClass(ArtistUpdateRequest.class);
        then(artistAdminService).should().update(eq(아티스트_id), 캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new ArtistUpdateRequest(
                "실리카겔", List.of("Silica Gel"), ArtistGenre.BAND,
                "https://instagram.com/silicagel", true));
    }

    @Test
    void 수정의_otherNames는_생략과_빈배열과_값있음이_서로_다르게_바인딩된다() {
        // given
        given(artistAdminService.update(anyLong(), any())).willReturn(아티스트_응답);

        // when
        mvc.patch().uri(단건_경로, 아티스트_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"실리카겔"}
                        """)
                .exchange();
        mvc.patch().uri(단건_경로, 아티스트_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"실리카겔","otherNames":[]}
                        """)
                .exchange();
        mvc.patch().uri(단건_경로, 아티스트_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"실리카겔","otherNames":["Silica Gel"]}
                        """)
                .exchange();

        // then
        ArgumentCaptor<ArtistUpdateRequest> 캡처 = ArgumentCaptor.forClass(ArtistUpdateRequest.class);
        then(artistAdminService).should(times(3)).update(eq(아티스트_id), 캡처.capture());
        List<ArtistUpdateRequest> 캡처된_요청들 = 캡처.getAllValues();
        assertThat(캡처된_요청들.get(0).otherNames()).isNull();
        assertThat(캡처된_요청들.get(1).otherNames()).isEmpty();
        assertThat(캡처된_요청들.get(2).otherNames()).containsExactly("Silica Gel");
    }

    @Test
    void 수정의_otherNames는_명시적_null도_생략과_같게_바인딩된다() {
        // given
        given(artistAdminService.update(anyLong(), any())).willReturn(아티스트_응답);

        // when
        mvc.patch().uri(단건_경로, 아티스트_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"실리카겔","otherNames":null}
                        """)
                .exchange();

        // then
        ArgumentCaptor<ArtistUpdateRequest> 캡처 = ArgumentCaptor.forClass(ArtistUpdateRequest.class);
        then(artistAdminService).should().update(eq(아티스트_id), 캡처.capture());
        assertThat(캡처.getValue().otherNames()).isNull();
    }

    @Test
    void 삭제는_204와_빈_본문을_반환하고_경로의_id를_서비스로_넘긴다() {
        // when
        MvcTestResult 결과 = mvc.delete().uri(단건_경로, 아티스트_id).exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.NO_CONTENT)
                .bodyText().isEmpty();
        then(artistAdminService).should().delete(아티스트_id);
    }

    @Test
    void 병합은_200과_ArtistMergeResponse_7필드를_반환한다() {
        // given
        given(artistMergeService.merge(any())).willReturn(병합_응답);

        // when
        MvcTestResult 결과 = mvc.post().uri(병합_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetId":7,"sourceIds":[8,9]}
                        """)
                .exchange();

        // then
        assertThat(결과).hasStatusOk()
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("targetId", "name", "mergedCount", "movedAppearances",
                        "removedDuplicates", "otherNames", "needsReview");
    }

    @Test
    void 병합_본문의_keepAliases는_생략하면_true로_바인딩된다() {
        // given
        given(artistMergeService.merge(any())).willReturn(병합_응답);

        // when
        mvc.post().uri(병합_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetId":7,"sourceIds":[8,9]}
                        """)
                .exchange();

        // then
        ArgumentCaptor<ArtistMergeRequest> 캡처 = ArgumentCaptor.forClass(ArtistMergeRequest.class);
        then(artistMergeService).should().merge(캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new ArtistMergeRequest(7L, List.of(8L, 9L), true));
    }

    @Test
    void 병합_본문의_keepAliases는_false로_보내면_false로_바인딩된다() {
        // given
        given(artistMergeService.merge(any())).willReturn(병합_응답);

        // when
        mvc.post().uri(병합_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetId":7,"sourceIds":[8,9],"keepAliases":false}
                        """)
                .exchange();

        // then
        ArgumentCaptor<ArtistMergeRequest> 캡처 = ArgumentCaptor.forClass(ArtistMergeRequest.class);
        then(artistMergeService).should().merge(캡처.capture());
        assertThat(캡처.getValue().keepAliases()).isFalse();
    }
}
