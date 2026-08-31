package com.greedy.festa.artist.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.greedy.festa.artist.entity.ArtistGenre;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class ArtistUpdateRequest {

    private String name;
    private List<String> otherNames;
    private ArtistGenre genre;
    private String instagramUrl;
    private Boolean needsReview;
    private boolean instagramUrlPresent;

    public ArtistUpdateRequest() {
    }

    public ArtistUpdateRequest(
            String name, List<String> otherNames, ArtistGenre genre,
            String instagramUrl, Boolean needsReview
    ) {
        this.name = name;
        this.otherNames = otherNames;
        this.genre = genre;
        this.instagramUrl = instagramUrl;
        this.needsReview = needsReview;
        this.instagramUrlPresent = instagramUrl != null;
    }

    public String name() { return name; }
    public List<String> otherNames() { return otherNames; }
    public ArtistGenre genre() { return genre; }
    public String instagramUrl() { return instagramUrl; }
    public Boolean needsReview() { return needsReview; }
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isInstagramUrlPresent() { return instagramUrlPresent; }

    public void setName(String name) {
        this.name = name;
    }

    public void setOtherNames(List<String> otherNames) { this.otherNames = otherNames; }
    public void setGenre(ArtistGenre genre) { this.genre = genre; }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
        this.instagramUrlPresent = true;
    }

    public void setNeedsReview(Boolean needsReview) { this.needsReview = needsReview; }
}
