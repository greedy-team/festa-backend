package com.greedy.festa.importer.controller;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.service.ImportPreviewService;
import com.greedy.festa.importer.service.ImportCommitService;
import com.greedy.festa.importer.service.ImportHistoryService;
import com.greedy.festa.importer.model.ImportBatchStatus;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.importer.dto.ImportHistoryItemResponse;
import com.greedy.festa.importer.dto.ImportHistoryResult;
import com.greedy.festa.importer.dto.ImportHistorySectionResult;
import com.greedy.festa.importer.dto.ImportCommitRequest;
import com.greedy.festa.importer.dto.ImportCommitResponse;
import com.greedy.festa.importer.dto.ImportCommitResult;
import com.greedy.festa.importer.dto.ImportCommitSectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ImportAdminControllerTest {

    @Mock ImportPreviewService importPreviewService;
    @Mock ImportCommitService importCommitService;
    @Mock ImportHistoryService importHistoryService;
    @InjectMocks ImportAdminController controller;

    @Test
    void history_기본_page_size와_빈_filter를_전달한다() throws Exception {
        given(importHistoryService.findAll(null, null, 0, 20))
                .willReturn(new PageResponse<>(java.util.List.of(), 0, 20, 0, 0, false, false));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/admin/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(importHistoryService).findAll(null, null, 0, 20);
    }

    @Test
    void history_type_status와_시간_result_JSON_계약을_반환한다() throws Exception {
        Instant uploadedAt = Instant.parse("2026-08-19T15:40:00Z");
        ImportHistorySectionResult empty = ImportHistorySectionResult.empty();
        ImportHistoryItemResponse item = new ImportHistoryItemResponse(
                37L, ImportBatchType.BUNDLE, java.util.List.of("festivals.csv"),
                ImportBatchStatus.COMMITTED, "haeun", uploadedAt,
                uploadedAt.plusSeconds(1800), uploadedAt.plusSeconds(600),
                new ImportHistoryResult(new ImportHistorySectionResult(1, 2, 3), empty, empty));
        given(importHistoryService.findAll(
                ImportBatchType.BUNDLE, ImportBatchStatus.COMMITTED, 1, 50))
                .willReturn(new PageResponse<>(java.util.List.of(item), 1, 50, 1, 1, false, true));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/admin/imports")
                        .param("type", "BUNDLE").param("status", "COMMITTED")
                        .param("page", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].importId").value(37))
                .andExpect(jsonPath("$.items[0].uploadedBy").value("haeun"))
                .andExpect(jsonPath("$.items[0].uploadedAt").value("2026-08-19T15:40:00Z"))
                .andExpect(jsonPath("$.items[0].expiresAt").value("2026-08-19T16:10:00Z"))
                .andExpect(jsonPath("$.items[0].committedAt").value("2026-08-19T15:50:00Z"))
                .andExpect(jsonPath("$.items[0].result.artists.created").value(1))
                .andExpect(jsonPath("$.items[0].result.artists.failed").doesNotExist());
    }

    @Test
    void history_지원하지_않는_type_status는_400_ErrorResponse다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(get("/admin/imports").param("type", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMPORT_INVALID_TYPE"));
        mockMvc.perform(get("/admin/imports").param("status", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMPORT_INVALID_STATUS"));
    }

    @Test
    void history_잘못된_page_size는_공통_400_ErrorResponse다() throws Exception {
        given(importHistoryService.findAll(null, null, -1, 20))
                .willThrow(new FestaException(CommonErrorCode.INVALID_PAGE));
        given(importHistoryService.findAll(null, null, 0, 51))
                .willThrow(new FestaException(CommonErrorCode.INVALID_PAGE_SIZE));
        given(importHistoryService.findAll(null, null, 0, 0))
                .willThrow(new FestaException(CommonErrorCode.INVALID_PAGE_SIZE));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(get("/admin/imports").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE"));
        mockMvc.perform(get("/admin/imports").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_SIZE"));
        mockMvc.perform(get("/admin/imports").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGE_SIZE"));
    }

    @Test
    void bundle_onConflict를_생략하면_UPDATE로_요청한다() {
        MockMultipartFile festivals = new MockMultipartFile("festivals", new byte[]{1});
        MockMultipartFile lineups = new MockMultipartFile("lineups", new byte[]{1});

        controller.previewBundle(festivals, lineups, null, null);

        verify(importPreviewService).previewBundle(
                eq(festivals), eq(lineups), eq(null), eq(ImportConflictPolicy.UPDATE), any(Instant.class));
    }

    @Test
    void single_path_type과_SKIP을_내부_타입으로_변환한다() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});
        ArgumentCaptor<Instant> instant = ArgumentCaptor.forClass(Instant.class);

        controller.previewSingle("artists", file, "skip");

        verify(importPreviewService).previewSingle(
                eq(ImportSection.ARTISTS), eq(file), eq(ImportConflictPolicy.SKIP), instant.capture());
        assertThat(instant.getValue()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "festivals,FESTIVALS",
            "lineups,LINEUPS",
            "artists,ARTISTS"
    })
    void single_3종_type을_지원한다(String path, ImportSection expected) {
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});

        controller.previewSingle(path, file, null);

        verify(importPreviewService).previewSingle(
                eq(expected), eq(file), eq(ImportConflictPolicy.UPDATE), any(Instant.class));
    }

    @Test
    void bundle_multipart_part를_API_계약대로_binding한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockMultipartFile festivals = new MockMultipartFile(
                "festivals", "festivals.csv", "text/csv", new byte[]{1});
        MockMultipartFile lineups = new MockMultipartFile(
                "lineups", "lineup.csv", "text/csv", new byte[]{1});
        MockMultipartFile artists = new MockMultipartFile(
                "artists", "artists.csv", "text/csv", new byte[]{1});
        MockMultipartFile onConflict = new MockMultipartFile(
                "onConflict", "", "text/plain", "SKIP".getBytes());

        mockMvc.perform(multipart("/admin/imports/bundle")
                        .file(festivals).file(lineups).file(artists).file(onConflict))
                .andExpect(status().isCreated());

        verify(importPreviewService).previewBundle(eq(festivals), eq(lineups), eq(artists),
                eq(ImportConflictPolicy.SKIP), any(Instant.class));
    }
    @Test
    void 잘못된_onConflict는_공통_오류로_거부한다() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});

        assertThatThrownBy(() -> controller.previewSingle("artists", file, "overwrite"))
                .isInstanceOf(FestaException.class)
                .extracting(exception -> ((FestaException) exception).getErrorCode())
                .isEqualTo(ImportErrorCode.IMPORT_INVALID_CONFLICT_POLICY);
    }

    @Test
    void multipart_용량_초과_예외를_413_PAYLOAD_TOO_LARGE로_매핑한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        MockMultipartFile festivals = new MockMultipartFile("festivals", new byte[]{1});
        MockMultipartFile lineups = new MockMultipartFile("lineups", new byte[]{1});
        given(importPreviewService.previewBundle(
                eq(festivals), eq(lineups), eq(null), eq(ImportConflictPolicy.UPDATE), any(Instant.class)))
                .willThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024));

        mockMvc.perform(multipart("/admin/imports/bundle").file(festivals).file(lineups))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void 필수_multipart_part_누락은_IMPORT_MISSING_FILE로_매핑한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        MockMultipartFile festivals = new MockMultipartFile("festivals", new byte[]{1});

        mockMvc.perform(multipart("/admin/imports/bundle").file(festivals))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMPORT_MISSING_FILE"));
    }

    @Test
    void commit_API는_Long_importId와_lines를_binding하고_200을_반환한다() throws Exception {
        ImportCommitSectionResult empty = new ImportCommitSectionResult(0, 0, 0, 0);
        given(importCommitService.commit(eq(37L), any(ImportCommitRequest.class)))
                .willReturn(new ImportCommitResponse(37L, Instant.EPOCH,
                        new ImportCommitResult(empty, empty, empty), java.util.List.of()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/admin/imports/37/commit")
                        .contentType("application/json")
                        .content("{\"lines\":{\"artists\":[1,2],\"festivals\":[1]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importId").value(37))
                .andExpect(jsonPath("$.result.lineups.updated").value(0))
                .andExpect(jsonPath("$.createdFestivalIds").isArray());
    }

    @Test
    void commit_FestaException은_공통_ErrorResponse로_매핑한다() throws Exception {
        given(importCommitService.commit(eq(37L), any()))
                .willThrow(new FestaException(ImportErrorCode.IMPORT_PREVIEW_STALE));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(post("/admin/imports/37/commit")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IMPORT_PREVIEW_STALE"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/admin/imports/37/commit"));
    }
}
