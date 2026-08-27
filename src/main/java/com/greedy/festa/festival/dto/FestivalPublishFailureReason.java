package com.greedy.festa.festival.dto;

import com.greedy.festa.festival.entity.FestivalPublishBlocker;

public enum FestivalPublishFailureReason {

    LINEUP_EMPTY,
    HOST_NOT_LINKED,
    COORDINATES_MISSING,
    NOT_FOUND;

    public static FestivalPublishFailureReason from(FestivalPublishBlocker blocker) {
        return switch (blocker) {
            case LINEUP_EMPTY -> LINEUP_EMPTY;
            case HOST_NOT_LINKED -> HOST_NOT_LINKED;
            case COORDINATES_MISSING -> COORDINATES_MISSING;
        };
    }
}
