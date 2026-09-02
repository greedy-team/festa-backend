package com.greedy.festa.global.util;

public class LikePatternUtils {

    private LikePatternUtils() {
    }

    public static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
