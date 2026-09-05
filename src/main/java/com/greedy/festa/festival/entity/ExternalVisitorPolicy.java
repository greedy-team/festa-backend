package com.greedy.festa.festival.entity;

public enum ExternalVisitorPolicy implements UnknownSafeEnum {
    ALLOWED,
    CONDITIONAL,
    DENIED,
    UNKNOWN;

    @Override
    public boolean isUnknown() {
        return this == UNKNOWN;
    }
}
