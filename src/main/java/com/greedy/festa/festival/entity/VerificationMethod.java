package com.greedy.festa.festival.entity;

public enum VerificationMethod implements UnknownSafeEnum {
    NONE,
    STUDENT_ID,
    PRE_BOOKING,
    INVITATION,
    OTHER,
    UNKNOWN;

    @Override
    public boolean isUnknown() {
        return this == UNKNOWN;
    }
}
