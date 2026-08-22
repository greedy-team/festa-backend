package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalCoverageItem;
import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.dto.FestivalCoverageStatus;
import com.greedy.festa.festival.dto.FestivalCoverageSummary;
import com.greedy.festa.festival.exception.FestivalCoverageErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.repository.HostCoverageRow;
import com.greedy.festa.host.repository.HostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FestivalCoverageService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HostRepository hostRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FestivalCoverageResponse findCoverage(
            Integer requestedYear, String requestedStatus, Pageable pageable
    ) {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        int year = requestedYear == null ? today.getYear() : requestedYear;
        validateYear(year, today.getYear());
        FestivalCoverageStatus statusFilter = parseStatus(requestedStatus);

        List<HostCoverageRow> rows = hostRepository.findCoverageRows(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year + 1, 1, 1),
                today
        );
        FestivalCoverageSummary summary = summarize(rows);
        List<FestivalCoverageItem> filteredItems = rows.stream()
                .map(row -> FestivalCoverageItem.of(row, resolveStatus(row)))
                .filter(item -> statusFilter == null
                        ? item.status() != FestivalCoverageStatus.PUBLISHED
                        : item.status() == statusFilter)
                .sorted(Comparator
                        .comparingInt((FestivalCoverageItem item) -> statusOrder(item.status()))
                        .thenComparing(FestivalCoverageItem::hostName))
                .toList();

        int fromIndex = (int) Math.min(pageable.getOffset(), filteredItems.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), filteredItems.size());
        List<FestivalCoverageItem> items = filteredItems.subList(fromIndex, toIndex);
        int totalPages = filteredItems.isEmpty()
                ? 0
                : (int) Math.ceil((double) filteredItems.size() / pageable.getPageSize());

        return new FestivalCoverageResponse(
                year,
                summary,
                items,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                filteredItems.size(),
                totalPages,
                pageable.getPageNumber() + 1 < totalPages,
                pageable.getPageNumber() > 0
        );
    }

    private FestivalCoverageStatus parseStatus(String requestedStatus) {
        if (requestedStatus == null) {
            return null;
        }
        try {
            return FestivalCoverageStatus.valueOf(requestedStatus);
        } catch (IllegalArgumentException e) {
            throw new FestaException(FestivalCoverageErrorCode.FESTIVAL_COVERAGE_INVALID_STATUS);
        }
    }

    private void validateYear(int year, int currentYear) {
        if (year < 2026 || year > currentYear + 1) {
            throw new FestaException(FestivalCoverageErrorCode.FESTIVAL_COVERAGE_INVALID_YEAR);
        }
    }

    private FestivalCoverageSummary summarize(List<HostCoverageRow> rows) {
        long published = count(rows, FestivalCoverageStatus.PUBLISHED);
        long reviewPending = count(rows, FestivalCoverageStatus.REVIEW_PENDING);
        long needsCheck = count(rows, FestivalCoverageStatus.NEEDS_CHECK);
        int coverageRate = rows.isEmpty()
                ? 0
                : (int) Math.round(published * 100.0 / rows.size());

        return new FestivalCoverageSummary(
                rows.size(), published, reviewPending, needsCheck, coverageRate
        );
    }

    private long count(List<HostCoverageRow> rows, FestivalCoverageStatus status) {
        return rows.stream().filter(row -> resolveStatus(row) == status).count();
    }

    private FestivalCoverageStatus resolveStatus(HostCoverageRow row) {
        if (row.getHasUnpublishedFestival()) {
            return FestivalCoverageStatus.REVIEW_PENDING;
        }
        if (!row.getHasCurrentFestival()) {
            return FestivalCoverageStatus.NEEDS_CHECK;
        }
        return FestivalCoverageStatus.PUBLISHED;
    }

    private int statusOrder(FestivalCoverageStatus status) {
        return switch (status) {
            case REVIEW_PENDING -> 0;
            case NEEDS_CHECK -> 1;
            case PUBLISHED -> 2;
        };
    }
}
