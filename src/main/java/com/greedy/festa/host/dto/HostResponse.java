package com.greedy.festa.host.dto;

import com.greedy.festa.host.entity.Host;

public record HostResponse(
        Long hostId, String name, String shortName, String region,
        String logoUrl, String bannerUrl, String homepageUrl, String instagramUrl, Long festivalCount
) {

    public static HostResponse of(Host host, Long festivalCount) {
        return new HostResponse(
                host.getId(), host.getName(), host.getShortName(), host.getRegion(),
                host.getLogoUrl(), host.getBannerUrl(), host.getHomepageUrl(),
                host.getInstagramUrl(), festivalCount
        );
    }
}
