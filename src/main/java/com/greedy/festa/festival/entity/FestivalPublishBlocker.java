package com.greedy.festa.festival.entity;

import com.greedy.festa.festival.exception.FestivalErrorCode;

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

    public FestivalErrorCode toErrorCode() {
        return switch (this) {
            case LINEUP_EMPTY -> FestivalErrorCode.FESTIVAL_PUBLISH_LINEUP_EMPTY;
            case HOST_NOT_LINKED -> FestivalErrorCode.FESTIVAL_PUBLISH_HOST_NOT_LINKED;
            case COORDINATES_MISSING -> FestivalErrorCode.FESTIVAL_PUBLISH_COORDINATES_MISSING;
        };
    }
}
