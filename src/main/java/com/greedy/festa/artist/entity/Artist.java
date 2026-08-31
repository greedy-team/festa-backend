package com.greedy.festa.artist.entity;

import com.greedy.festa.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Artist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    private ArtistGenre genre;

    private String imageUrl;

    private String instagramUrl;

    @Column(nullable = false)
    private boolean needsReview;

    @Builder
    private Artist(String name, ArtistGenre genre, String imageUrl,
                   String instagramUrl, boolean needsReview) {
        this.name = name;
        this.genre = genre;
        this.imageUrl = imageUrl;
        this.instagramUrl = instagramUrl;
        this.needsReview = needsReview;
    }

    public void updateFromImport(ArtistGenre genre, String imageUrl, boolean needsReview) {
        this.genre = genre;
        this.imageUrl = imageUrl;
        this.needsReview = needsReview;
    }

    public void update(String name, ArtistGenre genre,
                       String instagramUrl, Boolean needsReview) {
        if (name != null) {
            this.name = name;
        }
        if (genre != null) {
            this.genre = genre;
        }
        if (instagramUrl != null) {
            this.instagramUrl = instagramUrl.isBlank() ? null : instagramUrl;
        }
        if (needsReview != null) {
            this.needsReview = needsReview;
        }
    }

    public void markNeedsReview() {
        this.needsReview = true;
    }

    public void changeInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }
}
