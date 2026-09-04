package com.greedy.festa.global.util;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.search.exception.SearchErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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

    @Test
    void normalizesOptionalQueryWhilePreservingBlankAsNoFilter() {
        assertThat(LikePatternUtils.normalizeOptionalPattern(null, 50, ArtistErrorCode.ARTIST_INVALID_QUERY))
                .isNull();
        assertThat(LikePatternUtils.normalizeOptionalPattern("   ", 50, ArtistErrorCode.ARTIST_INVALID_QUERY))
                .isNull();
        assertThat(LikePatternUtils.normalizeOptionalPattern("  50%_\\  ", 50,
                ArtistErrorCode.ARTIST_INVALID_QUERY)).isEqualTo("50\\%\\_\\\\");
    }

    @Test
    void rejectsInvalidRequiredQuery() {
        assertThatExceptionOfType(FestaException.class)
                .isThrownBy(() -> LikePatternUtils.normalizeRequiredQuery(
                        "   ", 50, SearchErrorCode.SEARCH_INVALID_QUERY))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_INVALID_QUERY));
    }

    @Test
    void rejectsQueryLongerThanLimitAfterTrimming() {
        assertThatExceptionOfType(FestaException.class)
                .isThrownBy(() -> LikePatternUtils.normalizeOptionalPattern(
                        " " + "a".repeat(51) + " ", 50, ArtistErrorCode.ARTIST_INVALID_QUERY))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ArtistErrorCode.ARTIST_INVALID_QUERY));
    }
}
