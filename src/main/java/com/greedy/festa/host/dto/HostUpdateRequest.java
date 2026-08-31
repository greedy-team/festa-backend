package com.greedy.festa.host.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

public class HostUpdateRequest {

    private String name;
    private String shortName;
    private String region;
    private String logoUrl;
    private String bannerUrl;
    private String instagramUrl;
    private String homepageUrl;
    private boolean namePresent;
    private boolean shortNamePresent;
    private boolean regionPresent;
    private boolean logoUrlPresent;
    private boolean bannerUrlPresent;
    private boolean instagramUrlPresent;
    private boolean homepageUrlPresent;

    public HostUpdateRequest() {
    }

    public HostUpdateRequest(
            String name, String shortName, String region, String logoUrl,
            String bannerUrl, String instagramUrl, String homepageUrl
    ) {
        this.name = name;
        this.shortName = shortName;
        this.region = region;
        this.logoUrl = logoUrl;
        this.bannerUrl = bannerUrl;
        this.instagramUrl = instagramUrl;
        this.homepageUrl = homepageUrl;
        this.namePresent = name != null;
        this.shortNamePresent = shortName != null;
        this.regionPresent = region != null;
        this.logoUrlPresent = logoUrl != null;
        this.bannerUrlPresent = bannerUrl != null;
        this.instagramUrlPresent = instagramUrl != null;
        this.homepageUrlPresent = homepageUrl != null;
    }

    public String name() { return name; }
    public String shortName() { return shortName; }
    public String region() { return region; }
    public String logoUrl() { return logoUrl; }
    public String bannerUrl() { return bannerUrl; }
    public String instagramUrl() { return instagramUrl; }
    public String homepageUrl() { return homepageUrl; }
    @JsonIgnore @Schema(hidden = true) public boolean isNamePresent() { return namePresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isShortNamePresent() { return shortNamePresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isRegionPresent() { return regionPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isLogoUrlPresent() { return logoUrlPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isBannerUrlPresent() { return bannerUrlPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isInstagramUrlPresent() { return instagramUrlPresent; }
    @JsonIgnore @Schema(hidden = true) public boolean isHomepageUrlPresent() { return homepageUrlPresent; }

    public void setName(String value) { name = value; namePresent = true; }
    public void setShortName(String value) { shortName = value; shortNamePresent = true; }
    public void setRegion(String value) { region = value; regionPresent = true; }
    public void setLogoUrl(String value) { logoUrl = value; logoUrlPresent = true; }
    public void setBannerUrl(String value) { bannerUrl = value; bannerUrlPresent = true; }
    public void setInstagramUrl(String value) { instagramUrl = value; instagramUrlPresent = true; }
    public void setHomepageUrl(String value) { homepageUrl = value; homepageUrlPresent = true; }
}
