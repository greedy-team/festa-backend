package com.greedy.festa.global.util;

import java.util.Objects;

public class LikePatternUtils {

    private LikePatternUtils() {
    }

    /** Escapes PostgreSQL LIKE metacharacters so the input is matched literally. */
    public static String escape(String value) {
        String nonNullValue = Objects.requireNonNull(value, "value must not be null");
        return nonNullValue.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
