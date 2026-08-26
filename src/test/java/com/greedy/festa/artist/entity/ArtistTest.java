package com.greedy.festa.artist.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistTest {

    @Test
    void 모든_수정값이_null이면_기존_값을_유지한다() {
        Artist artist = artist();

        artist.update(null, null, null, null);

        assertThat(artist.getName()).isEqualTo("아티스트");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.BAND);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/original");
        assertThat(artist.isNeedsReview()).isTrue();
    }

    @Test
    void name만_수정한다() {
        Artist artist = artist();

        artist.update("변경된 아티스트", null, null, null);

        assertThat(artist.getName()).isEqualTo("변경된 아티스트");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.BAND);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/original");
        assertThat(artist.isNeedsReview()).isTrue();
    }

    @Test
    void genre만_수정한다() {
        Artist artist = artist();

        artist.update(null, ArtistGenre.HIPHOP, null, null);

        assertThat(artist.getName()).isEqualTo("아티스트");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.HIPHOP);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/original");
        assertThat(artist.isNeedsReview()).isTrue();
    }

    @Test
    void instagramUrl만_수정한다() {
        Artist artist = artist();

        artist.update(null, null, "https://instagram.com/updated", null);

        assertThat(artist.getName()).isEqualTo("아티스트");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.BAND);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/updated");
        assertThat(artist.isNeedsReview()).isTrue();
    }

    @Test
    void instagramUrl이_null이면_기존_값을_유지한다() {
        Artist artist = artist();

        artist.update("변경된 아티스트", null, null, null);

        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/original");
    }

    @Test
    void instagramUrl이_빈_문자열이면_null로_변경한다() {
        Artist artist = artist();

        artist.update(null, null, "", null);

        assertThat(artist.getInstagramUrl()).isNull();
    }

    @Test
    void instagramUrl이_공백_문자열이면_null로_변경한다() {
        Artist artist = artist();

        artist.update(null, null, "   ", null);

        assertThat(artist.getInstagramUrl()).isNull();
    }

    @Test
    void needsReview를_true에서_false로_수정한다() {
        Artist artist = artist();

        artist.update(null, null, null, false);

        assertThat(artist.getName()).isEqualTo("아티스트");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.BAND);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/original");
        assertThat(artist.isNeedsReview()).isFalse();
    }

    @Test
    void 일부_필드만_수정하고_나머지는_유지한다() {
        Artist artist = artist();

        artist.update(null, ArtistGenre.DANCE,
                "https://instagram.com/updated", null);

        assertThat(artist.getName()).isEqualTo("아티스트");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.DANCE);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/updated");
        assertThat(artist.isNeedsReview()).isTrue();
    }

    @Test
    void update해도_imageUrl은_기존_값을_유지한다() {
        Artist artist = artist();

        artist.update("변경된 아티스트", ArtistGenre.HIPHOP,
                "https://instagram.com/updated", false);

        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
    }

    @Test
    void updateFromImport_overwrites_only_import_fields() {
        Artist artist = artist();

        artist.updateFromImport(ArtistGenre.HIPHOP,
                "https://image.example.com/imported.jpg", false);

        assertThat(artist.getName()).isEqualTo("아티스트");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.HIPHOP);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/imported.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/original");
        assertThat(artist.isNeedsReview()).isFalse();
    }

    private Artist artist() {
        return Artist.builder()
                .name("아티스트")
                .genre(ArtistGenre.BAND)
                .imageUrl("https://image.example.com/original.jpg")
                .instagramUrl("https://instagram.com/original")
                .needsReview(true)
                .build();
    }
}
