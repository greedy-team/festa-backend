package com.greedy.festa.importer.service;

import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.importer.dto.ImportHistoryItemResponse;
import com.greedy.festa.importer.dto.ImportHistoryResult;
import com.greedy.festa.importer.dto.ImportHistorySectionResult;
import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.entity.ImportCommitAction;
import com.greedy.festa.importer.entity.ImportCommitSection;
import com.greedy.festa.importer.model.ImportBatchStatus;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import com.greedy.festa.importer.repository.ImportCommitAggregateRow;
import com.greedy.festa.importer.repository.ImportCommitRowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImportHistoryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort HISTORY_SORT = Sort.by(
            Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));

    private final ImportBatchRepository importBatchRepository;
    private final ImportCommitRowRepository importCommitRowRepository;
    private final Clock importClock;

    @Transactional(readOnly = true)
    public PageResponse<ImportHistoryItemResponse> findAll(
            ImportBatchType type, ImportBatchStatus status, int page, int size
    ) {
        validatePagination(page, size);
        Instant now = importClock.instant();
        Page<ImportBatch> batches = importBatchRepository.findHistory(
                type, status, now,
                PageRequest.of(page, size, HISTORY_SORT));

        List<Long> committedBatchIds = batches.getContent().stream()
                .filter(batch -> batch.getCommittedAt() != null)
                .map(ImportBatch::getId)
                .toList();
        Map<Long, ImportHistoryResult> results = aggregateResults(committedBatchIds);

        return PageResponse.from(batches.map(batch -> toResponse(batch, now, results)));
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
    }

    private Map<Long, ImportHistoryResult> aggregateResults(List<Long> batchIds) {
        if (batchIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CountsBySection> counts = new HashMap<>();
        for (ImportCommitAggregateRow row : importCommitRowRepository.aggregateByBatchIds(batchIds)) {
            counts.computeIfAbsent(row.getBatchId(), ignored -> new CountsBySection())
                    .add(row.getSection(), row.getAction(), row.getTotal());
        }
        Map<Long, ImportHistoryResult> results = new HashMap<>();
        batchIds.forEach(id -> results.put(id,
                counts.getOrDefault(id, new CountsBySection()).toResult()));
        return results;
    }

    private ImportHistoryItemResponse toResponse(
            ImportBatch batch, Instant now, Map<Long, ImportHistoryResult> results
    ) {
        ImportBatchStatus status = statusOf(batch, now);
        return new ImportHistoryItemResponse(
                batch.getId(),
                batch.getType(),
                Collections.unmodifiableList(new ArrayList<>(batch.getFileNames())),
                status,
                batch.getUploadedByAdmin() == null ? null : batch.getUploadedByAdmin().getUsername(),
                batch.getUploadedAt(),
                batch.getExpiresAt(),
                batch.getCommittedAt(),
                status == ImportBatchStatus.COMMITTED ? results.get(batch.getId()) : null);
    }

    private ImportBatchStatus statusOf(ImportBatch batch, Instant now) {
        if (batch.getCommittedAt() != null) {
            return ImportBatchStatus.COMMITTED;
        }
        if (!batch.getExpiresAt().isAfter(now)) {
            return ImportBatchStatus.EXPIRED;
        }
        return ImportBatchStatus.PENDING;
    }

    private static final class CountsBySection {
        private final Map<ImportCommitSection, ActionCounts> values =
                new EnumMap<>(ImportCommitSection.class);

        private void add(ImportCommitSection section, ImportCommitAction action, long count) {
            values.computeIfAbsent(section, ignored -> new ActionCounts()).add(action, count);
        }

        private ImportHistoryResult toResult() {
            return new ImportHistoryResult(
                    result(ImportCommitSection.ARTISTS),
                    result(ImportCommitSection.FESTIVALS),
                    result(ImportCommitSection.LINEUPS));
        }

        private ImportHistorySectionResult result(ImportCommitSection section) {
            return values.getOrDefault(section, new ActionCounts()).toResult();
        }
    }

    private static final class ActionCounts {
        private long created;
        private long updated;
        private long skipped;

        private void add(ImportCommitAction action, long count) {
            switch (action) {
                case CREATE -> created += count;
                case UPDATE -> updated += count;
                case SKIP -> skipped += count;
            }
        }

        private ImportHistorySectionResult toResult() {
            return new ImportHistorySectionResult(created, updated, skipped);
        }
    }
}
