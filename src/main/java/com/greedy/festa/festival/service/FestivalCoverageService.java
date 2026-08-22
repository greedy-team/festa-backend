package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalCoverageItem;
import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.dto.FestivalCoverageStatus;
import com.greedy.festa.festival.dto.FestivalCoverageSummary;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.repository.HostCoverageRow;
import com.greedy.festa.host.repository.HostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
    private static final int MAX_PAGE_SIZE = 50;

    private final HostRepository hostRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FestivalCoverageResponse findCoverage(
            Integer requestedYear, String requestedStatus, int page, int size
    ) {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        int year = today.getYear();
        if (requestedYear != null) {
            year = requestedYear;
        }
        validateYear(year, today.getYear());
        validatePagination(page, size);
        FestivalCoverageStatus statusFilter = parseStatus(requestedStatus);

        List<HostCoverageRow> rows = hostRepository.findCoverageRows(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year + 1, 1, 1),
                today
        );
        FestivalCoverageSummary summary = summarize(rows);
        List<FestivalCoverageItem> filteredItems = rows.stream()
                .map(row -> FestivalCoverageItem.of(row, resolveStatus(row)))
                .filter(item -> shouldInclude(item, statusFilter))
                .sorted(Comparator
                        .comparingInt((FestivalCoverageItem item) -> statusOrder(item.status()))
                        .thenComparing(FestivalCoverageItem::hostName))
                .toList();

        PageRequest pageRequest = PageRequest.of(page, size);
        int fromIndex = (int) Math.min(pageRequest.getOffset(), filteredItems.size());
        int toIndex = Math.min(fromIndex + size, filteredItems.size());
        List<FestivalCoverageItem> items = filteredItems.subList(fromIndex, toIndex);
        PageResponse<FestivalCoverageItem> hosts = PageResponse.from(
                new PageImpl<>(items, pageRequest, filteredItems.size()));

        return new FestivalCoverageResponse(year, summary, hosts);
    }

    private boolean shouldInclude(
            FestivalCoverageItem item, FestivalCoverageStatus statusFilter
    ) {
        if (statusFilter == null) {
            return item.status() != FestivalCoverageStatus.PUBLISHED;
        }
        return item.status() == statusFilter;
    }

    private FestivalCoverageStatus parseStatus(String requestedStatus) {
        if (requestedStatus == null) {
            return null;
        }
        try {
            return FestivalCoverageStatus.valueOf(requestedStatus);
        } catch (IllegalArgumentException e) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_COVERAGE_INVALID_STATUS);
        }
    }

    private void validateYear(int year, int currentYear) {
        if (year < 2026 || year > currentYear + 1) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_COVERAGE_INVALID_YEAR);
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
    }

    private FestivalCoverageSummary summarize(List<HostCoverageRow> rows) {
        long published = count(rows, FestivalCoverageStatus.PUBLISHED);
        long reviewPending = count(rows, FestivalCoverageStatus.REVIEW_PENDING);
        long needsCheck = count(rows, FestivalCoverageStatus.NEEDS_CHECK);
        int coverageRate = 0;
        if (!rows.isEmpty()) {
            coverageRate = (int) Math.round(published * 100.0 / rows.size());
        }

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
