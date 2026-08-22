package com.greedy.festa.importer.controller;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.GlobalExceptionHandler;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.service.ImportPreviewService;
import com.greedy.festa.importer.service.ImportCommitService;
import com.greedy.festa.importer.dto.ImportCommitRequest;
import com.greedy.festa.importer.dto.ImportCommitResponse;
import com.greedy.festa.importer.dto.ImportCommitResult;
import com.greedy.festa.importer.dto.ImportCommitSectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ImportAdminControllerTest {

    @Mock ImportPreviewService importPreviewService;
    @Mock ImportCommitService importCommitService;
    ImportAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new ImportAdminController(importPreviewService, importCommitService,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
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

    @Test
    void 빈_selection은_400으로_매핑한다() throws Exception {
        given(importCommitService.commit(eq(37L), any()))
                .willThrow(new FestaException(ImportErrorCode.IMPORT_INVALID_LINE_SELECTION));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(post("/admin/imports/37/commit")
                        .contentType("application/json").content("{\"lines\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IMPORT_INVALID_LINE_SELECTION"));
    }

    @Test
    void 일반_multipart_part_누락은_Import_전용_오류로_매핑하지_않는다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleMissingServletRequestPartException(
                new MissingServletRequestPartException("file"), new MockHttpServletRequest());

        assertThat(response.getBody().errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST_BODY.name());
    }
}
