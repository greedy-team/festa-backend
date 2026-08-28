package com.greedy.festa.artist.entity;

import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.global.exception.FestaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ArtistGenreTest {

    @Test
    void 값이_없으면_장르_필터를_적용하지_않는다() {
        assertThat(ArtistGenre.from(null)).isNull();
        assertThat(ArtistGenre.from(" ")).isNull();
    }

    @Test
    void 장르_문자열을_enum으로_변환한다() {
        assertThat(ArtistGenre.from("BAND")).isEqualTo(ArtistGenre.BAND);
    }

    @Test
    void 지원하지_않는_장르는_계약된_예외로_거부한다() {
        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> ArtistGenre.from("ROCK"));

        assertThat(thrown.getErrorCode()).isEqualTo(ArtistErrorCode.ARTIST_INVALID_GENRE_TYPE);
    }
}
