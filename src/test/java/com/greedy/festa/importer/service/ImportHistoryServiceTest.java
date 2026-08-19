package com.greedy.festa.importer.service;

import com.greedy.festa.admin.entity.AdminUser;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.entity.ImportCommitAction;
import com.greedy.festa.importer.entity.ImportCommitSection;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.model.ImportBatchStatus;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import com.greedy.festa.importer.repository.ImportCommitAggregateRow;
import com.greedy.festa.importer.repository.ImportCommitRowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ImportHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T16:10:00Z");

    @Mock ImportBatchRepository batchRepository;
    @Mock ImportCommitRowRepository auditRepository;
    ImportHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ImportHistoryService(batchRepository, auditRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void Clock_경계와_committedAt_우선순위로_상태를_판정한다() {
        ImportBatch pending = batch(1L, NOW.minusSeconds(60), null, null);
        ImportBatch expiredAtNow = batch(2L, NOW.minusSeconds(1800), null, null);
        ImportBatch committedAfterExpiry = batch(
                3L, NOW.minusSeconds(3600), NOW.minusSeconds(10), null);
        given(batchRepository.findHistory(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(pending, expiredAtNow, committedAfterExpiry)));
        given(auditRepository.aggregateByBatchIds(List.of(3L))).willReturn(List.of());

        var response = service.findAll(null, null, 0, 20);

        assertThat(response.items()).extracting(item -> item.status())
                .containsExactly(ImportBatchStatus.PENDING, ImportBatchStatus.EXPIRED,
                        ImportBatchStatus.COMMITTED);
        assertThat(response.items().get(0).result()).isNull();
        assertThat(response.items().get(1).result()).isNull();
        assertThat(response.items().get(2).result()).isNotNull();
        assertThat(response.items().get(2).result().artists().created()).isZero();
    }

    @Test
    void type_status_now와_고정_정렬을_DB_page_조회에_전달한다() {
        given(batchRepository.findHistory(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service.findAll(ImportBatchType.BUNDLE, ImportBatchStatus.EXPIRED, 2, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(batchRepository).findHistory(
                eq(ImportBatchType.BUNDLE), eq("EXPIRED"), eq(NOW), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("uploadedAt").getDirection().isDescending())
                .isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection().isDescending())
                .isTrue();
    }

    @Test
    void 현재_page의_COMMITTED_ID만_한번에_집계하고_실제_action을_result로_변환한다() {
        ImportBatch committed = batch(37L, NOW.minusSeconds(100), NOW.minusSeconds(20), null);
        ImportBatch pending = batch(38L, NOW.minusSeconds(10), null, null);
        given(batchRepository.findHistory(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(committed, pending)));
        List<ImportCommitAggregateRow> aggregates = List.of(
                aggregate(37L, ImportCommitSection.ARTISTS, ImportCommitAction.CREATE, 2),
                aggregate(37L, ImportCommitSection.ARTISTS, ImportCommitAction.UPDATE, 1),
                aggregate(37L, ImportCommitSection.FESTIVALS, ImportCommitAction.SKIP, 3),
                aggregate(37L, ImportCommitSection.LINEUPS, ImportCommitAction.CREATE, 5));
        given(auditRepository.aggregateByBatchIds(List.of(37L))).willReturn(aggregates);

        var response = service.findAll(null, null, 0, 20);

        verify(auditRepository).aggregateByBatchIds(List.of(37L));
        var result = response.items().getFirst().result();
        assertThat(result.artists().created()).isEqualTo(2);
        assertThat(result.artists().updated()).isOne();
        assertThat(result.artists().skipped()).isZero();
        assertThat(result.festivals().skipped()).isEqualTo(3);
        assertThat(result.lineups().created()).isEqualTo(5);
        assertThat(result.lineups().updated()).isZero();
        assertThat(response.items().get(1).result()).isNull();
    }

    @Test
    void COMMITTED가_없으면_aggregate_query를_실행하지_않는다() {
        given(batchRepository.findHistory(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(batch(1L, NOW.minusSeconds(1), null, null))));

        service.findAll(null, null, 0, 20);

        verify(auditRepository, never()).aggregateByBatchIds(any());
    }

    @Test
    void username과_세_시간을_반환하고_preview를_변경하지_않는다() {
        AdminUser admin = AdminUser.builder().username("haeun").passwordHash("hash").build();
        ImportBatch batch = batch(37L, NOW.minusSeconds(100), NOW.minusSeconds(20), admin);
        String previewBefore = batch.getPreview();
        given(batchRepository.findHistory(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(batch)));
        given(auditRepository.aggregateByBatchIds(List.of(37L))).willReturn(List.of());

        var item = service.findAll(null, null, 0, 20).items().getFirst();

        assertThat(item.uploadedBy()).isEqualTo("haeun");
        assertThat(item.uploadedAt()).isEqualTo(NOW.minusSeconds(100));
        assertThat(item.expiresAt()).isEqualTo(NOW.minusSeconds(100).plusSeconds(1800));
        assertThat(item.committedAt()).isEqualTo(NOW.minusSeconds(20));
        assertThat(batch.getPreview()).isEqualTo(previewBefore);
    }

    @Test
    void uploadedByAdmin이_null이면_uploadedBy도_null이다() {
        ImportBatch batch = batch(37L, NOW.minusSeconds(100), NOW.minusSeconds(20), null);
        given(batchRepository.findHistory(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(batch)));
        given(auditRepository.aggregateByBatchIds(List.of(37L))).willReturn(List.of());

        assertThat(service.findAll(null, null, 0, 20).items().getFirst().uploadedBy()).isNull();
    }

    @Test
    void page와_size_범위를_검증한다() {
        assertError(() -> service.findAll(null, null, -1, 20), CommonErrorCode.INVALID_PAGE);
        assertError(() -> service.findAll(null, null, 0, 0), CommonErrorCode.INVALID_PAGE_SIZE);
        assertError(() -> service.findAll(null, null, 0, 51), CommonErrorCode.INVALID_PAGE_SIZE);
    }

    @Test
    void size_최솟값_1을_허용한다() {
        given(batchRepository.findHistory(any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service.findAll(null, null, 0, 1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(batchRepository).findHistory(any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isOne();
    }

    @Test
    void history_service는_readOnly_transaction이다() throws Exception {
        Transactional transactional = ImportHistoryService.class
                .getMethod("findAll", ImportBatchType.class, ImportBatchStatus.class,
                        int.class, int.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private ImportBatch batch(Long id, Instant uploadedAt, Instant committedAt, AdminUser admin) {
        ImportBatch batch = ImportBatch.builder()
                .type(ImportBatchType.BUNDLE)
                .fileNames(List.of("artists.csv", "festivals.csv", "lineup.csv"))
                .onConflict(ImportConflictPolicy.UPDATE)
                .preview("preview-must-remain")
                .uploadedByAdmin(admin)
                .uploadedAt(uploadedAt)
                .committedAt(committedAt)
                .build();
        ReflectionTestUtils.setField(batch, "id", id);
        return batch;
    }

    private ImportCommitAggregateRow aggregate(
            Long batchId, ImportCommitSection section, ImportCommitAction action, long total
    ) {
        ImportCommitAggregateRow row = mock(ImportCommitAggregateRow.class);
        given(row.getBatchId()).willReturn(batchId);
        given(row.getSection()).willReturn(section);
        given(row.getAction()).willReturn(action);
        given(row.getTotal()).willReturn(total);
        return row;
    }

    private void assertError(Runnable action, CommonErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOf(FestaException.class)
                .extracting(error -> ((FestaException) error).getErrorCode())
                .isEqualTo(expected);
    }
}
