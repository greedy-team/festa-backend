package com.greedy.festa.festival.entity;

public enum TicketType implements UnknownSafeEnum {
    FREE,
    PAID,
    UNKNOWN;

    @Override
    public boolean isUnknown() {
        return this == UNKNOWN;
    }
}
