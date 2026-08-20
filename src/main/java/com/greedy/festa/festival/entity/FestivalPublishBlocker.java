package com.greedy.festa.festival.entity;

import java.util.ArrayList;
import java.util.List;

public enum FestivalPublishBlocker {

    LINEUP_EMPTY,
    HOST_NOT_LINKED,
    COORDINATES_MISSING;

    public static List<FestivalPublishBlocker> evaluate(
            boolean hostLinked, Double latitude, Double longitude, long lineupCount
    ) {
        List<FestivalPublishBlocker> blockers = new ArrayList<>();

        if (lineupCount == 0) {
            blockers.add(LINEUP_EMPTY);
        }
        if (!hostLinked) {
            blockers.add(HOST_NOT_LINKED);
        }
        if (latitude == null || longitude == null) {
            blockers.add(COORDINATES_MISSING);
        }

        return List.copyOf(blockers);
    }
}
