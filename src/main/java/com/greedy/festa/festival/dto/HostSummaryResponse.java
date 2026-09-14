package com.greedy.festa.festival.dto;

import com.greedy.festa.host.entity.Host;

public record HostSummaryResponse(Long id, String name, String logoUrl) {

    public static HostSummaryResponse from(Host host) {
        return new HostSummaryResponse(host.getId(), host.getName(), host.getLogoUrl());
    }
}
