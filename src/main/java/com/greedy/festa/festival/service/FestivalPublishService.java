package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalAdminSortType;
import com.greedy.festa.festival.dto.FestivalBatchPublishResponse;
import com.greedy.festa.festival.dto.FestivalPublishFailure;
import com.greedy.festa.festival.dto.FestivalPublishFailureReason;
import com.greedy.festa.festival.dto.FestivalPublishResponse;
import com.greedy.festa.festival.dto.FestivalReviewItem;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.FestivalPublishBlocker;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.festival.repository.FestivalWithLineupCount;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.logging.AfterCommitLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalPublishService {

    private static final int MAX_BATCH_SIZE = 100;

    private final FestivalRepository festivalRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FestivalReviewItem> findAll(
            Boolean published, Long hostId, Integer year, String q, String discovery,
            FestivalAdminSortType sort, int page, int size
    ) {
        if (page < 0) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > 50) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE_SIZE);
        }

        LocalDate yearStart = null;
        LocalDate nextYearStart = null;
        if (year != null) {
            yearStart = LocalDate.of(year, 1, 1);
            nextYearStart = LocalDate.of(year + 1, 1, 1);
        }

        Page<FestivalWithLineupCount> rows = festivalRepository.findReviewRows(
                published, hostId, yearStart, nextYearStart, q, discovery,
                PageRequest.of(page, size, sort.toSort())
        );

        return PageResponse.from(rows.map(row -> {
            Festival festival = row.getFestival();
            long lineupCount = row.getLineupCount();
            List<FestivalPublishBlocker> blockers = FestivalPublishBlocker.evaluate(
                    row.getHost() != null,
                    festival.getLatitude(),
                    festival.getLongitude(),
                    lineupCount
            );
            return FestivalReviewItem.of(festival, row.getHost(), lineupCount, blockers);
        }));
    }

    @Transactional
    public FestivalPublishResponse publish(Long id) {
        Festival festival = festivalRepository.findById(id)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));

        if (festival.getPublishedAt() != null) {
            return FestivalPublishResponse.of(festival);
        }

        long lineupCount = festivalRepository.countLineupsByFestivalId(id);
        List<FestivalPublishBlocker> blockers = FestivalPublishBlocker.evaluate(
                festival.getHost() != null,
                festival.getLatitude(),
                festival.getLongitude(),
                lineupCount
        );

        if (!blockers.isEmpty()) {
            throw new FestaException(blockers.getFirst().toErrorCode());
        }

        festival.publish(Instant.now(clock));
        return FestivalPublishResponse.of(festival);
    }

    @Transactional
    public FestivalPublishResponse unpublish(Long id) {
        Festival festival = festivalRepository.findById(id)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        festival.unpublish();
        // publishedAt이 null로 덮여 발행됐던 흔적이 데이터에서 사라진다.
        AfterCommitLogger.info(log, "축제 발행 취소 - festivalId={}", festival.getId());
        return FestivalPublishResponse.of(festival);
    }

    @Transactional
    public FestivalBatchPublishResponse batchPublish(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_BATCH_SIZE || ids.stream().anyMatch(Objects::isNull)) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_IDS);
        }

        List<Long> requestedIds = ids.stream().distinct().toList();
        Map<Long, FestivalWithLineupCount> rowsById = festivalRepository.findPublishTargets(requestedIds)
                .stream()
                .collect(Collectors.toMap(row -> row.getFestival().getId(), Function.identity()));

        Instant publishedAt = Instant.now(clock);
        List<Long> publishedIds = new ArrayList<>();
        List<FestivalPublishFailure> failed = new ArrayList<>();

        for (Long id : requestedIds) {
            FestivalWithLineupCount row = rowsById.get(id);
            if (row == null) {
                failed.add(new FestivalPublishFailure(
                        id, FestivalPublishFailureReason.NOT_FOUND
                ));
                continue;
            }

            Festival festival = row.getFestival();
            if (festival.getPublishedAt() != null) {
                publishedIds.add(id);
                continue;
            }

            List<FestivalPublishBlocker> blockers = FestivalPublishBlocker.evaluate(
                    row.getHost() != null,
                    festival.getLatitude(),
                    festival.getLongitude(),
                    row.getLineupCount()
            );
            if (!blockers.isEmpty()) {
                failed.add(new FestivalPublishFailure(
                        id, FestivalPublishFailureReason.from(blockers.getFirst())
                ));
                continue;
            }

            festival.publish(publishedAt);
            publishedIds.add(id);
        }

        // 요약은 로그에만 남긴다. 응답은 publishedIds와 failed 둘로 고정돼 건수를 담지 않는다.
        AfterCommitLogger.info(log, "축제 일괄 발행 - 요청 {}건, 발행 {}건, 실패 {}건{}",
                requestedIds.size(), publishedIds.size(), failed.size(), failureSummary(failed));

        return new FestivalBatchPublishResponse(publishedIds, failed);
    }

    private String failureSummary(List<FestivalPublishFailure> failed) {
        if (failed.isEmpty()) {
            return "";
        }
        return " (실패=" + failed.stream()
                .map(failure -> failure.festivalId() + ":" + failure.reason())
                .collect(Collectors.joining(", ")) + ")";
    }
}
