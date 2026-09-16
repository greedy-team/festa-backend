package com.greedy.festa.artist.entity;

import com.greedy.festa.support.fixture.ArtistFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistTest {

    @Test
    void updateKeepsGenreWhenItIsNull() {
        Artist artist = artist();

        artist.update("updated artist", null);

        assertThat(artist.getName()).isEqualTo("updated artist");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.BAND);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
    }

    @Test
    void updateChangesOnlyNameAndGenre() {
        Artist artist = artist();

        artist.update("updated artist", ArtistGenre.HIPHOP);
        artist.changeInstagramUrl("https://instagram.com/updated");

        assertThat(artist.getName()).isEqualTo("updated artist");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.HIPHOP);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/original.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/updated");
    }

    @Test
    void updateFromImportOverwritesOnlyImportFields() {
        Artist artist = artist();

        artist.updateFromImport(ArtistGenre.HIPHOP, "https://image.example.com/imported.jpg");

        assertThat(artist.getName()).isEqualTo("artist");
        assertThat(artist.getGenre()).isEqualTo(ArtistGenre.HIPHOP);
        assertThat(artist.getImageUrl()).isEqualTo("https://image.example.com/imported.jpg");
        assertThat(artist.getInstagramUrl()).isEqualTo("https://instagram.com/original");
    }

    private Artist artist() {
        return ArtistFixture.artist("artist")
                .genre(ArtistGenre.BAND)
                .imageUrl("https://image.example.com/original.jpg")
                .instagramUrl("https://instagram.com/original")
                .build();
    }
}
