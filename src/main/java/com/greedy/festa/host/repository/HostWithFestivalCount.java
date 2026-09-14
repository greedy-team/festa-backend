package com.greedy.festa.host.repository;

import com.greedy.festa.host.entity.Host;

public interface HostWithFestivalCount {
    Host getHost();
    Long getFestivalCount();
}
