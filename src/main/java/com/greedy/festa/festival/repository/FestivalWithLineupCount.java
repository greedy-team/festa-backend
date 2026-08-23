package com.greedy.festa.festival.repository;

import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.host.entity.Host;

public interface FestivalWithLineupCount {

    Festival getFestival();

    Host getHost();

    Long getLineupCount();
}
