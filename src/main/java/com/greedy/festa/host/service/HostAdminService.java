package com.greedy.festa.host.service;

import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.dto.HostCreateRequest;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.dto.HostUpdateRequest;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.global.logging.AfterCommitLogger;
import com.greedy.festa.host.repository.HostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HostAdminService {

    private final HostRepository hostRepository;

    @Transactional
    public HostResponse create(HostCreateRequest request) {
        validateName(request.name());
        validateRegion(request.region());

        if (hostRepository.existsByName(request.name())) {
            throw new FestaException(HostErrorCode.HOST_DUPLICATE_NAME);
        }

        Host host = hostRepository.save(Host.builder()
                .name(request.name())
                .shortName(request.shortName())
                .region(request.region())
                .logoUrl(request.logoUrl())
                .bannerUrl(request.bannerUrl())
                .homepageUrl(request.homepageUrl())
                .instagramUrl(request.instagramUrl())
                .build());

        return HostResponse.of(host, 0L);
    }

    @Transactional(readOnly = true)
    public PageResponse<HostResponse> findAll(int page, int size) {
        if (page < 0) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > 50) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE_SIZE);
        }

        return PageResponse.from(
                hostRepository.findAllWithFestivalCount(PageRequest.of(page, size))
                        .map(row -> HostResponse.of(
                                row.getHost(),
                                row.getFestivalCount()
                        ))
        );
    }

    @Transactional(readOnly = true)
    public HostResponse findOne(Long id) {
        Host host = hostRepository.findById(id)
                .orElseThrow(() -> new FestaException(HostErrorCode.HOST_NOT_FOUND));
        return HostResponse.of(host, hostRepository.countFestivalsByHostId(id));
    }

    @Transactional
    public HostResponse update(Long id, HostUpdateRequest request) {
        Host host = hostRepository.findById(id)
                .orElseThrow(() -> new FestaException(HostErrorCode.HOST_NOT_FOUND));

        validateName(request.name());
        if (hostRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new FestaException(HostErrorCode.HOST_DUPLICATE_NAME);
        }
        validateRegion(request.region());

        host.changeName(request.name());
        host.changeRegion(request.region());
        host.changeShortName(blankToNull(request.shortName()));
        host.changeLogoUrl(blankToNull(request.logoUrl()));
        host.changeBannerUrl(blankToNull(request.bannerUrl()));
        host.changeInstagramUrl(blankToNull(request.instagramUrl()));
        host.changeHomepageUrl(blankToNull(request.homepageUrl()));

        return HostResponse.of(host, hostRepository.countFestivalsByHostId(id));
    }

    @Transactional
    public void delete(Long id) {
        Host host = hostRepository.findById(id)
                .orElseThrow(() -> new FestaException(HostErrorCode.HOST_NOT_FOUND));

        if (hostRepository.countFestivalsByHostId(id) > 0) {
            throw new FestaException(HostErrorCode.HOST_HAS_FESTIVALS);
        }

        hostRepository.delete(host);
        AfterCommitLogger.info(log, "주최 삭제 - hostId={}", id);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new FestaException(HostErrorCode.HOST_INVALID_NAME);
        }
    }

    private void validateRegion(String region) {
        if (region == null || region.isBlank() || region.length() > 50) {
            throw new FestaException(HostErrorCode.HOST_INVALID_REGION);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
