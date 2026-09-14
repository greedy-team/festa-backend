package com.greedy.festa.festival.dto;

import com.greedy.festa.host.entity.Host;

public record FestivalHostResponse(
        Long id, String name, String logoUrl,
        String instagramUrl, String homepageUrl
) {

    public static FestivalHostResponse from(Host host) {
        return new FestivalHostResponse(
                host.getId(), host.getName(), host.getLogoUrl(),
                host.getInstagramUrl(), host.getHomepageUrl()
        );
    }
}
