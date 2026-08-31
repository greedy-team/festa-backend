package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalAdminDetailResponse;
import com.greedy.festa.festival.dto.FestivalBatchPublishResponse;
import com.greedy.festa.festival.dto.FestivalPublishFailure;
import com.greedy.festa.festival.dto.FestivalPublishFailureReason;
import com.greedy.festa.festival.dto.FestivalPublishResponse;
import com.greedy.festa.festival.dto.FestivalReviewItem;
import com.greedy.festa.festival.dto.FestivalSortType;
import com.greedy.festa.festival.dto.FestivalUpdateRequest;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.FestivalPublishBlocker;
import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.festival.repository.FestivalWithLineupCount;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class FestivalAdminService {

    private static final int MAX_BATCH_SIZE = 100;

    private final FestivalRepository festivalRepository;
    private final HostRepository hostRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FestivalReviewItem> findAll(
            Boolean published, Long hostId, Integer year, String q, String discovery,
            FestivalSortType sort, int page, int size
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

    @Transactional(readOnly = true)
    public FestivalAdminDetailResponse findOne(Long id) {
        Festival festival = festivalRepository.findDetailById(id)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        long lineupCount = festivalRepository.countLineupsByFestivalId(id);
        List<FestivalPublishBlocker> blockers = FestivalPublishBlocker.evaluate(
                festival.getHost() != null,
                festival.getLatitude(),
                festival.getLongitude(),
                lineupCount
        );
        return FestivalAdminDetailResponse.of(festival, lineupCount, blockers);
    }

    @Transactional
    public FestivalAdminDetailResponse update(Long id, FestivalUpdateRequest request) {
        Festival festival = festivalRepository.findDetailById(id)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        require(request.isNamePresent(), FestivalErrorCode.FESTIVAL_INVALID_NAME);
        require(request.isStartDatePresent() && request.isEndDatePresent(), FestivalErrorCode.FESTIVAL_INVALID_DATE);
        require(request.isHostIdPresent(), FestivalErrorCode.FESTIVAL_INVALID_HOST_ID);
        require(request.isImportKeyPresent() && request.isPosterUrlPresent() && request.isDescriptionPresent()
                        && request.isVenueNamePresent() && request.isAddressPresent()
                        && request.isAdmissionNotePresent() && request.isInstagramUrlPresent(),
                FestivalErrorCode.FESTIVAL_INVALID_OPTIONAL_STRING);

        String name = requiredString(request.name(), FestivalErrorCode.FESTIVAL_INVALID_NAME);
        LocalDate startDate = parseRequired(request.startDate(), LocalDate.class, FestivalErrorCode.FESTIVAL_INVALID_DATE);
        LocalDate endDate = parseRequired(request.endDate(), LocalDate.class, FestivalErrorCode.FESTIVAL_INVALID_DATE);
        if (endDate.isBefore(startDate)) throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_DATE);
        Long hostId = parseRequired(request.hostId(), Long.class, FestivalErrorCode.FESTIVAL_INVALID_HOST_ID);
        Host host = hostRepository.findById(hostId)
                .orElseThrow(() -> new FestaException(HostErrorCode.HOST_NOT_FOUND));

        festival.updateByAdmin(host, optionalString(request.importKey()), name, startDate, endDate,
                optionalString(request.posterUrl()), optionalString(request.description()),
                optionalString(request.venueName()), optionalString(request.address()),
                parseOptional(request.latitude(), Double.class), parseOptional(request.longitude(), Double.class),
                parseOptional(request.externalVisitor(), ExternalVisitorPolicy.class),
                parseOptional(request.verification(), VerificationMethod.class),
                parseOptional(request.ticketType(), TicketType.class),
                parseOptional(request.ticketOpenAt(), Instant.class),
                optionalString(request.admissionNote()), optionalString(request.instagramUrl()));
        return findOne(id);
    }

    private void require(boolean condition, FestivalErrorCode code) {
        if (!condition) throw new FestaException(code);
    }

    private String requiredString(String value, FestivalErrorCode code) {
        if (value == null || value.isBlank()) throw new FestaException(code);
        return value.trim();
    }

    private String optionalString(String value) {
        if (value == null) throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_OPTIONAL_STRING);
        return value.isBlank() ? null : value.trim();
    }

    private <T> T parseRequired(JsonNode node, Class<T> type, FestivalErrorCode code) {
        T value = parse(node, type, code);
        if (value == null) throw new FestaException(code);
        return value;
    }

    private <T> T parseOptional(JsonNode node, Class<T> type) {
        return parse(node, type, FestivalErrorCode.FESTIVAL_INVALID_UPDATE_VALUE);
    }

    private <T> T parse(JsonNode node, Class<T> type, FestivalErrorCode code) {
        if (node == null || node.isNull() || (node.isTextual() && node.asText().isBlank())) return null;
        try {
            String value = node.isTextual() ? node.asText().trim() : node.asText();
            if (type == Long.class) return type.cast(Long.valueOf(value));
            if (type == Double.class) return type.cast(Double.valueOf(value));
            if (type == LocalDate.class) return type.cast(LocalDate.parse(value));
            if (type == Instant.class) return type.cast(Instant.parse(value));
            if (type.isEnum()) {
                @SuppressWarnings({"unchecked", "rawtypes"}) T parsed = (T) Enum.valueOf((Class) type, value);
                return parsed;
            }
            throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new FestaException(code);
        }
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

        return new FestivalBatchPublishResponse(publishedIds, failed);
    }
}
