package com.greedy.festa.host.entity;

import com.greedy.festa.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class Host extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String shortName;

    @Column(nullable = false)
    private String region;

    private String bannerUrl;
    private String homepageUrl;
    private String instagramUrl;

    @Builder
    private Host(String name, String shortName, String region,
                 String bannerUrl, String homepageUrl, String instagramUrl) {
        this.name = name;
        this.shortName = shortName;
        this.region = region;
        this.bannerUrl = bannerUrl;
        this.homepageUrl = homepageUrl;
        this.instagramUrl = instagramUrl;
    }
}
