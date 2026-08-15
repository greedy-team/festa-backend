package com.greedy.festa.host.service;

import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.dto.HostCreateRequest;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.dto.HostUpdateRequest;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.exception.HostErrorCode;
import com.greedy.festa.host.repository.HostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HostService {

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
    public PageResponse<HostResponse> findAll(Pageable pageable) {
        return PageResponse.from(
                hostRepository.findAllWithFestivalCount(pageable)
                        .map(row -> HostResponse.of(
                                row.getHost(),
                                row.getFestivalCount()
                        ))
        );
    }

    @Transactional
    public HostResponse update(Long id, HostUpdateRequest request) {
        Host host = hostRepository.findById(id)
                .orElseThrow(() -> new FestaException(HostErrorCode.HOST_NOT_FOUND));

        if (request.name() != null) {
            validateName(request.name());
            if (hostRepository.existsByNameAndIdNot(request.name(), id)) {
                throw new FestaException(HostErrorCode.HOST_DUPLICATE_NAME);
            }
            host.changeName(request.name());
        }

        if (request.region() != null) {
            validateRegion(request.region());
            host.changeRegion(request.region());
        }

        if (request.shortName() != null) {
            host.changeShortName(blankToNull(request.shortName()));
        }
        if (request.logoUrl() != null){
            host.changeLogoUrl(blankToNull(request.logoUrl()));
        }
        if (request.bannerUrl() != null){
            host.changeBannerUrl(blankToNull(request.bannerUrl()));
        }
        if (request.homepageUrl() != null) {
            host.changeHomepageUrl(blankToNull(request.homepageUrl()));
        }
        if (request.instagramUrl() != null) {
            host.changeInstagramUrl(blankToNull(request.instagramUrl()));
        }

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
        if (value.isBlank()) {
            return null;
        }
        return value;
    }
}
