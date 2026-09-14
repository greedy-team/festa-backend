package com.greedy.festa.support.fixture;

import com.greedy.festa.host.entity.Host;

public final class HostFixture {

    public static final String REGION = "서울";

    private HostFixture() {
    }

    public static Host.HostBuilder host(String name) {
        return Host.builder()
                .name(name)
                .region(REGION);
    }
}
