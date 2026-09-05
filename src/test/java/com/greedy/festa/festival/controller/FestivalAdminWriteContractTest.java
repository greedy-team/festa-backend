package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalBatchPublishResponse;
import com.greedy.festa.festival.dto.FestivalCreateRequest;
import com.greedy.festa.festival.dto.FestivalPublishFailure;
import com.greedy.festa.festival.dto.FestivalPublishFailureReason;
import com.greedy.festa.festival.dto.FestivalPublishResponse;
import com.greedy.festa.festival.dto.FestivalResponse;
import com.greedy.festa.festival.dto.FestivalUpdateRequest;
import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import com.greedy.festa.festival.service.FestivalAdminService;
import com.greedy.festa.festival.service.FestivalCoverageService;
import com.greedy.festa.festival.service.FestivalPublishService;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(controllers = FestivalAdminController.class,
        excludeAutoConfiguration = OAuth2ClientWebSecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("NonAsciiCharacters")
class FestivalAdminWriteContractTest {

    private static final String 목록_경로 = "/api/admin/festivals";
    private static final String 단건_경로 = "/api/admin/festivals/{id}";
    private static final String 일괄_발행_경로 = "/api/admin/festivals/publish";
    private static final String 단건_발행_경로 = "/api/admin/festivals/{id}/publish";
    private static final long 축제_id = 7L;

    private static final String 열일곱_필드_본문 = """
            {"hostId":3,"importKey":"sejong-2026-daedong","name":"대동제",
             "startDate":"2026-05-20","endDate":"2026-05-22",
             "posterUrl":"https://cdn.example/poster.png","description":"봄 축제",
             "venueName":"대양홀","address":"서울 광진구 능동로 209",
             "latitude":37.5509,"longitude":127.0739,
             "externalVisitor":"CONDITIONAL","verification":"STUDENT_ID","ticketType":"PAID",
             "ticketOpenAt":"2026-05-01T03:04:05Z",
             "admissionNote":"재학생 무료","instagramUrl":"https://instagram.com/sejong"}
            """;

    private static final FestivalResponse 축제_응답 = new FestivalResponse(
            축제_id, 3L, "세종대학교 총학생회", "sejong-2026-daedong", "대동제",
            LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 22),
            "https://cdn.example/poster.png", "봄 축제", "대양홀", "서울 광진구 능동로 209",
            37.5509, 127.0739,
            ExternalVisitorPolicy.CONDITIONAL, VerificationMethod.STUDENT_ID, TicketType.PAID,
            Instant.parse("2026-05-01T03:04:05Z"), "재학생 무료", "https://instagram.com/sejong",
            Instant.parse("2026-05-10T06:07:08Z"), 3L, List.of());

    private static final FestivalPublishResponse 발행_응답 = new FestivalPublishResponse(
            축제_id, "대동제", Instant.parse("2026-05-10T06:07:08Z"));

    private static final FestivalBatchPublishResponse 일괄_발행_응답 = new FestivalBatchPublishResponse(
            List.of(7L, 8L),
            List.of(new FestivalPublishFailure(9L, FestivalPublishFailureReason.LINEUP_EMPTY)));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private FestivalAdminService festivalAdminService;

    @MockitoBean
    private FestivalPublishService festivalPublishService;

    @MockitoBean
    private FestivalCoverageService festivalCoverageService;

    @Test
    void 등록은_201과_FestivalResponse_22필드를_반환한다() {
        // given
        given(festivalAdminService.create(any())).willReturn(축제_응답);

        // when
        MvcTestResult 결과 = mvc.post().uri(목록_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content(열일곱_필드_본문)
                .exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("festivalId", "hostId", "hostName", "importKey", "name",
                        "startDate", "endDate", "posterUrl", "description", "venueName", "address",
                        "latitude", "longitude", "externalVisitor", "verification", "ticketType",
                        "ticketOpenAt", "admissionNote", "instagramUrl", "publishedAt",
                        "lineupCount", "blockers");
    }

    @Test
    void 등록_본문은_FestivalCreateRequest_17필드로_바인딩된다() {
        // given
        given(festivalAdminService.create(any())).willReturn(축제_응답);

        // when
        mvc.post().uri(목록_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content(열일곱_필드_본문)
                .exchange();

        // then
        ArgumentCaptor<FestivalCreateRequest> 캡처 = ArgumentCaptor.forClass(FestivalCreateRequest.class);
        then(festivalAdminService).should().create(캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new FestivalCreateRequest(
                3L, "sejong-2026-daedong", "대동제",
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 22),
                "https://cdn.example/poster.png", "봄 축제", "대양홀", "서울 광진구 능동로 209",
                37.5509, 127.0739,
                ExternalVisitorPolicy.CONDITIONAL, VerificationMethod.STUDENT_ID, TicketType.PAID,
                Instant.parse("2026-05-01T03:04:05Z"), "재학생 무료",
                "https://instagram.com/sejong"));
    }

    @Test
    void 등록_본문에서_생략한_선택_필드는_null로_바인딩된다() {
        // given
        given(festivalAdminService.create(any())).willReturn(축제_응답);

        // when
        mvc.post().uri(목록_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"hostId":3,"name":"대동제","startDate":"2026-05-20","endDate":"2026-05-22"}
                        """)
                .exchange();

        // then
        ArgumentCaptor<FestivalCreateRequest> 캡처 = ArgumentCaptor.forClass(FestivalCreateRequest.class);
        then(festivalAdminService).should().create(캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new FestivalCreateRequest(
                3L, null, "대동제",
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 22),
                null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void 수정은_PATCH로_200과_FestivalResponse_22필드를_반환한다() {
        // given
        given(festivalAdminService.update(anyLong(), any())).willReturn(축제_응답);

        // when
        MvcTestResult 결과 = mvc.patch().uri(단건_경로, 축제_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(열일곱_필드_본문)
                .exchange();

        // then
        assertThat(결과).hasStatusOk()
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("festivalId", "hostId", "hostName", "importKey", "name",
                        "startDate", "endDate", "posterUrl", "description", "venueName", "address",
                        "latitude", "longitude", "externalVisitor", "verification", "ticketType",
                        "ticketOpenAt", "admissionNote", "instagramUrl", "publishedAt",
                        "lineupCount", "blockers");
    }

    @Test
    void 수정은_경로의_id와_FestivalUpdateRequest_17필드를_서비스로_넘긴다() {
        // given
        given(festivalAdminService.update(anyLong(), any())).willReturn(축제_응답);

        // when
        mvc.patch().uri(단건_경로, 축제_id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(열일곱_필드_본문)
                .exchange();

        // then
        ArgumentCaptor<FestivalUpdateRequest> 캡처 = ArgumentCaptor.forClass(FestivalUpdateRequest.class);
        then(festivalAdminService).should().update(eq(축제_id), 캡처.capture());
        assertThat(캡처.getValue()).isEqualTo(new FestivalUpdateRequest(
                3L, "sejong-2026-daedong", "대동제",
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 22),
                "https://cdn.example/poster.png", "봄 축제", "대양홀", "서울 광진구 능동로 209",
                37.5509, 127.0739,
                ExternalVisitorPolicy.CONDITIONAL, VerificationMethod.STUDENT_ID, TicketType.PAID,
                Instant.parse("2026-05-01T03:04:05Z"), "재학생 무료",
                "https://instagram.com/sejong"));
    }

    @Test
    void 삭제는_204와_빈_본문을_반환하고_경로의_id를_서비스로_넘긴다() {
        // when
        MvcTestResult 결과 = mvc.delete().uri(단건_경로, 축제_id).exchange();

        // then
        assertThat(결과).hasStatus(HttpStatus.NO_CONTENT)
                .bodyText().isEmpty();
        then(festivalAdminService).should().delete(축제_id);
    }

    @Test
    void 일괄_발행은_200과_publishedIds_failed_2필드를_반환한다() {
        // given
        given(festivalPublishService.batchPublish(any())).willReturn(일괄_발행_응답);

        // when
        MvcTestResult 결과 = mvc.post().uri(일괄_발행_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"festivalIds":[7,8,9]}
                        """)
                .exchange();

        // then
        assertThat(결과).hasStatusOk()
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("publishedIds", "failed");
        assertThat(결과).bodyJson().extractingPath("$.failed[0]").asMap()
                .containsOnlyKeys("festivalId", "reason");
        assertThat(결과).bodyJson().extractingPath("$.failed[0].reason").asString()
                .isEqualTo("LINEUP_EMPTY");
    }

    @Test
    void 일괄_발행_본문의_festivalIds는_Long_목록으로_서비스에_전달된다() {
        // given
        given(festivalPublishService.batchPublish(any())).willReturn(일괄_발행_응답);

        // when
        mvc.post().uri(일괄_발행_경로)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"festivalIds":[7,8,9]}
                        """)
                .exchange();

        // then
        then(festivalPublishService).should().batchPublish(List.of(7L, 8L, 9L));
    }

    @Test
    void 단건_발행은_POST로_200과_FestivalPublishResponse_3필드를_반환한다() {
        // given
        given(festivalPublishService.publish(anyLong())).willReturn(발행_응답);

        // when
        MvcTestResult 결과 = mvc.post().uri(단건_발행_경로, 축제_id).exchange();

        // then
        assertThat(결과).hasStatusOk()
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("festivalId", "name", "publishedAt");
        then(festivalPublishService).should().publish(축제_id);
    }

    @Test
    void 발행_취소는_DELETE로_200과_FestivalPublishResponse_3필드를_반환한다() {
        // given
        given(festivalPublishService.unpublish(anyLong())).willReturn(발행_응답);

        // when
        MvcTestResult 결과 = mvc.delete().uri(단건_발행_경로, 축제_id).exchange();

        // then
        assertThat(결과).hasStatusOk()
                .bodyJson().extractingPath("$").asMap()
                .containsOnlyKeys("festivalId", "name", "publishedAt");
        then(festivalPublishService).should().unpublish(축제_id);
    }
}
