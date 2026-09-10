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
    void internalSpacesStillCountTowardLimitAndRemainInQuery() {
        String fifty = "a".repeat(25) + " " + "b".repeat(24);
        assertThat(LikePatternUtils.normalizeRequiredQuery(" " + fifty + " ", 50,
                SearchErrorCode.SEARCH_INVALID_QUERY)).isEqualTo(fifty);
        assertThat(LikePatternUtils.normalizeOptionalPattern(" " + fifty + " ", 50,
                ArtistErrorCode.ARTIST_INVALID_QUERY)).isEqualTo("a".repeat(25) + "b".repeat(24));
        assertThatExceptionOfType(FestaException.class).isThrownBy(() ->
                LikePatternUtils.normalizeRequiredQuery(fifty + "b", 50, SearchErrorCode.SEARCH_INVALID_QUERY));
        assertThatExceptionOfType(FestaException.class).isThrownBy(() ->
                LikePatternUtils.normalizeOptionalPattern(fifty + "b", 50, ArtistErrorCode.ARTIST_INVALID_QUERY));
    }

    @Test
    void escapesBackslashBeforeLikeWildcards() {
        assertThat(LikePatternUtils.escape("a\\b%c_d"))
                .isEqualTo("a\\\\b\\%c\\_d");
    }

    @Test
    void searchPatternRemovesOnlyAsciiSpacesBeforeEscaping() {
        assertThat(LikePatternUtils.toSearchPattern("a \\ % _ \t\n\u00a0\u3000-. b"))
                .isEqualTo("a\\\\\\%\\_\t\n\u00a0\u3000-.b");
        assertThat(LikePatternUtils.normalizeOptionalPattern("  a \\ % _ b  ", 50,
                ArtistErrorCode.ARTIST_INVALID_QUERY)).isEqualTo("a\\\\\\%\\_b");
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
