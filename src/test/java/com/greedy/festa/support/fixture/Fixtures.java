package com.greedy.festa.support.fixture;

import org.springframework.test.util.ReflectionTestUtils;

public final class Fixtures {

    private Fixtures() {
    }

    public static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
