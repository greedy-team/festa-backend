package com.greedy.festa.importer.entity;

import com.greedy.festa.admin.entity.AdminUser;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportBatchType type;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> fileNames;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportConflictPolicy onConflict;

    private String preview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_admin_id")
    private AdminUser uploadedByAdmin;

    @Column(nullable = false)
    private Instant uploadedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant committedAt;

    @Builder
    private ImportBatch(ImportBatchType type, List<String> fileNames,
                        ImportConflictPolicy onConflict, String preview,
                        AdminUser uploadedByAdmin, Instant uploadedAt,
                        Instant committedAt) {
        this.type = type;
        this.fileNames = fileNames;
        this.onConflict = onConflict;
        this.preview = preview;
        this.uploadedByAdmin = uploadedByAdmin;
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt");
        this.expiresAt = this.uploadedAt.plus(30, ChronoUnit.MINUTES);
        this.committedAt = committedAt;
    }
}
