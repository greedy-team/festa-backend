package com.greedy.festa.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class LikePatternUtilsTest {

    @Test
    void escapesBackslashBeforeLikeWildcards() {
        assertThat(LikePatternUtils.escape("a\\b%c_d"))
                .isEqualTo("a\\\\b\\%c\\_d");
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> LikePatternUtils.escape(null))
                .withMessage("value must not be null");
    }
}
