package com.greedy.festa.festival.service;

import com.greedy.festa.festival.dto.FestivalCreateRequest;
import com.greedy.festa.festival.dto.FestivalUpdateRequest;
import com.greedy.festa.festival.dto.FestivalResponse;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.exception.FestivalErrorCode;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.lineup.repository.LineupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalAdminService {

    private final FestivalRepository festivalRepository;
    private final LineupRepository lineupRepository;
    private final HostRepository hostRepository;

    @Transactional
    public FestivalResponse create(FestivalCreateRequest request) {
        String name = blankToNull(request.name());
        validateName(name);
        validatePeriod(request.startDate(), request.endDate());
        validateHostId(request.hostId());

        String importKey = blankToNull(request.importKey());
        if (importKey != null && festivalRepository.existsByImportKey(importKey)) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_DUPLICATE_IMPORT_KEY);
        }

        Host host = findHost(request.hostId());

        Festival festival = festivalRepository.save(Festival.builder()
                .host(host)
                .importKey(importKey)
                .name(name)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .posterUrl(blankToNull(request.posterUrl()))
                .description(blankToNull(request.description()))
                .venueName(blankToNull(request.venueName()))
                .address(blankToNull(request.address()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .externalVisitor(request.externalVisitor())
                .verification(request.verification())
                .ticketType(request.ticketType())
                .ticketOpenAt(request.ticketOpenAt())
                .admissionNote(blankToNull(request.admissionNote()))
                .instagramUrl(blankToNull(request.instagramUrl()))
                .build());

        return FestivalResponse.of(festival, 0L);
    }

    @Transactional(readOnly = true)
    public FestivalResponse findOne(Long id) {
        Festival festival = festivalRepository.findDetailById(id)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        return FestivalResponse.of(festival, lineupRepository.countByFestivalId(id));
    }

    @Transactional
    public FestivalResponse update(Long id, FestivalUpdateRequest request) {
        Festival festival = festivalRepository.findById(id)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));

        String name = blankToNull(request.name());
        validateName(name);
        validatePeriod(request.startDate(), request.endDate());
        validateHostId(request.hostId());
        validatePeriodCoversLineup(id, request.startDate(), request.endDate());
        validateCoordinatesKept(festival, request.latitude(), request.longitude());

        String importKey = blankToNull(request.importKey());
        if (importKey != null && festivalRepository.existsByImportKeyAndIdNot(importKey, id)) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_DUPLICATE_IMPORT_KEY);
        }

        festival.update(
                findHost(request.hostId()), request.importKey(), name,
                request.startDate(), request.endDate(),
                request.posterUrl(), request.description(),
                request.venueName(), request.address(),
                request.latitude(), request.longitude(),
                request.externalVisitor(), request.verification(),
                request.ticketType(), request.ticketOpenAt(),
                request.admissionNote(), request.instagramUrl()
        );

        return FestivalResponse.of(festival, lineupRepository.countByFestivalId(id));
    }

    @Transactional
    public void delete(Long id) {
        Festival festival = festivalRepository.findById(id)
                .orElseThrow(() -> new FestaException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        if (festival.getPublishedAt() != null) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_ALREADY_PUBLISHED);
        }
        if (lineupRepository.existsByFestivalId(id)) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_HAS_LINEUPS);
        }

        festivalRepository.delete(festival);
        // 임포트 감사 행은 SET NULL로 링크만 잃고 남아 그쪽으로 되짚을 수 없다.
        log.info("축제 삭제 - festivalId={}", id);
    }

    private Host findHost(Long hostId) {
        return hostRepository.findById(hostId)
                .orElseThrow(() -> new FestaException(HostErrorCode.HOST_NOT_FOUND));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            return null;
        }
        return trimmedValue;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_NAME);
        }
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_START_DATE);
        }
        if (endDate == null) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_END_DATE);
        }
        if (startDate.isAfter(endDate)) {
            throw new FestaException(CommonErrorCode.INVALID_DATE_RANGE);
        }
    }

    private void validateHostId(Long hostId) {
        if (hostId == null) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_INVALID_HOST_ID);
        }
    }

    private void validateCoordinatesKept(Festival festival, Double latitude, Double longitude) {
        if (festival.getPublishedAt() == null) {
            return;
        }
        if (latitude == null || longitude == null) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_PUBLISHED_COORDINATES_REQUIRED);
        }
    }

    private void validatePeriodCoversLineup(Long festivalId, LocalDate startDate, LocalDate endDate) {
        Integer maxDay = lineupRepository.findMaxDayByFestivalId(festivalId);
        if (maxDay == null) {
            return;
        }
        if (!Festival.withinPeriod(startDate, endDate, maxDay)) {
            throw new FestaException(FestivalErrorCode.FESTIVAL_PERIOD_CONFLICTS_LINEUP);
        }
    }
}
