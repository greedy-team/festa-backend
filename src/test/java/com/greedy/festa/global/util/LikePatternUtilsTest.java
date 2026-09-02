package com.greedy.festa.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternUtilsTest {

    @Test
    void escapesBackslashBeforeLikeWildcards() {
        assertThat(LikePatternUtils.escape("a\\b%c_d"))
                .isEqualTo("a\\\\b\\%c\\_d");
    }
}
