package com.greedy.festa.importer.entity;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.festival.entity.Festival;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportCommitRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportCommitSection section;

    @Column(nullable = false)
    private int line;

    @Column(nullable = false)
    private String importKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportCommitAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id")
    private Festival festival;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private ImportCommitPayload payload;

    @Column(nullable = false)
    private Instant committedAt;

    @Builder
    private ImportCommitRow(ImportBatch batch, ImportCommitSection section,
                            int line, String importKey,
                            ImportCommitAction action, Festival festival,
                            Artist artist, ImportCommitPayload payload,
                            Instant committedAt) {
        this.batch = batch;
        this.section = section;
        this.line = line;
        this.importKey = importKey;
        this.action = action;
        this.festival = festival;
        this.artist = artist;
        this.payload = payload;
        this.committedAt = committedAt;
    }
}
