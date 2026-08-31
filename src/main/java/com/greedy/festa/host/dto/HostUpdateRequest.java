package com.greedy.festa.host.dto;

public record HostUpdateRequest(
        String name,
        String shortName,
        String region,
        String logoUrl,
        String bannerUrl,
        String instagramUrl,
        String homepageUrl
) {
}
