package com.greedy.festa.artist.entity;

import com.greedy.festa.festival.entity.Festival;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uq_lineup_festival_day_display_order",
        columnNames = {"festival_id", "day", "display_order"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lineup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festival festival;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    @Column(nullable = false)
    private int day;

    @Column(nullable = false)
    private int displayOrder;

    @Builder
    private Lineup(Festival festival, Artist artist, int day, int displayOrder) {
        this.festival = festival;
        this.artist = artist;
        this.day = day;
        this.displayOrder = displayOrder;
    }
}
