package com.greedy.festa.festival.entity;

import com.greedy.festa.global.entity.BaseEntity;
import com.greedy.festa.host.entity.Host;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Festival extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id")
    private Host host;

    private String importKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private String posterUrl;
    private String description;

    private String venueName;
    private String address;
    private Double latitude;
    private Double longitude;

    private ExternalVisitorPolicy externalVisitor;

    private VerificationMethod verification;

    private TicketType ticketType;

    @Transient
    private ExternalVisitorPolicy loadedExternalVisitor;

    @Transient
    private VerificationMethod loadedVerification;

    @Transient
    private TicketType loadedTicketType;

    private Instant ticketOpenAt;
    private String admissionNote;
    private String admissionRaw;

    private String instagramUrl;

    private Instant publishedAt;

    private String discovery;

    private String crawlFlag;

    private String sourceUrl;
    private Instant importedAt;

    @Builder
    private Festival(Host host, String importKey, String name,
                     LocalDate startDate, LocalDate endDate,
                     String posterUrl, String description,
                     String venueName, String address,
                     Double latitude, Double longitude,
                     ExternalVisitorPolicy externalVisitor,
                     VerificationMethod verification,
                     TicketType ticketType, Instant ticketOpenAt,
                     String admissionNote, String admissionRaw,
                     String instagramUrl,
                     String discovery, String crawlFlag,
                     String sourceUrl, Instant importedAt) {
        this.host = host;
        this.importKey = importKey;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.posterUrl = posterUrl;
        this.description = description;
        this.venueName = venueName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.externalVisitor = externalVisitor;
        this.verification = verification;
        this.ticketType = ticketType;
        this.ticketOpenAt = ticketOpenAt;
        this.admissionNote = admissionNote;
        this.admissionRaw = admissionRaw;
        this.instagramUrl = instagramUrl;
        this.discovery = discovery;
        this.crawlFlag = crawlFlag;
        this.sourceUrl = sourceUrl;
        this.importedAt = importedAt;
    }

    public void updateFromImport(
            Host host, String name, LocalDate startDate, LocalDate endDate,
            String posterUrl, String description, String venueName,
            Double latitude, Double longitude,
            ExternalVisitorPolicy externalVisitor, VerificationMethod verification,
            TicketType ticketType, Instant ticketOpenAt, String admissionRaw,
            String instagramUrl, String discovery, String crawlFlag,
            String sourceUrl, Instant importedAt
    ) {
        this.host = host;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.posterUrl = posterUrl;
        this.description = description;
        this.venueName = venueName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.externalVisitor = preserveUnknown(this.externalVisitor, externalVisitor);
        this.verification = preserveUnknown(this.verification, verification);
        this.ticketType = preserveUnknown(this.ticketType, ticketType);
        this.ticketOpenAt = ticketOpenAt;
        this.admissionRaw = admissionRaw;
        this.instagramUrl = instagramUrl;
        this.discovery = discovery;
        this.crawlFlag = crawlFlag;
        this.sourceUrl = sourceUrl;
        this.importedAt = importedAt;
    }

    public void update(
            Host host, String importKey, String name,
            LocalDate startDate, LocalDate endDate,
            String posterUrl, String description,
            String venueName, String address,
            Double latitude, Double longitude,
            ExternalVisitorPolicy externalVisitor, VerificationMethod verification,
            TicketType ticketType, Instant ticketOpenAt,
            String admissionNote, String instagramUrl
    ) {
        this.host = host;
        this.importKey = blankToNull(importKey);
        this.name = name.trim();
        this.startDate = startDate;
        this.endDate = endDate;
        this.posterUrl = blankToNull(posterUrl);
        this.description = blankToNull(description);
        this.venueName = blankToNull(venueName);
        this.address = blankToNull(address);
        this.latitude = latitude;
        this.longitude = longitude;
        this.externalVisitor = preserveUnknown(this.externalVisitor, externalVisitor);
        this.verification = preserveUnknown(this.verification, verification);
        this.ticketType = preserveUnknown(this.ticketType, ticketType);
        this.ticketOpenAt = ticketOpenAt;
        this.admissionNote = blankToNull(admissionNote);
        this.instagramUrl = blankToNull(instagramUrl);
    }

    public void publish(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void unpublish() {
        this.publishedAt = null;
    }

    public boolean hasUnknownAdmissionValue() {
        return isUnknown(externalVisitor) || isUnknown(verification) || isUnknown(ticketType);
    }

    @PostLoad
    private void rememberLoadedAdmissionValues() {
        loadedExternalVisitor = externalVisitor;
        loadedVerification = verification;
        loadedTicketType = ticketType;
    }

    @PrePersist
    private void preventUnknownAdmissionValuesOnCreate() {
        if (hasUnknownAdmissionValue()) {
            throw new IllegalStateException("UNKNOWN 입장 정책은 저장할 수 없습니다.");
        }
    }

    @PreUpdate
    private void preventNewUnknownAdmissionValues() {
        if (becameUnknown(loadedExternalVisitor, externalVisitor)
                || becameUnknown(loadedVerification, verification)
                || becameUnknown(loadedTicketType, ticketType)) {
            throw new IllegalStateException("UNKNOWN admission values cannot be stored.");
        }
    }

    private static <E extends Enum<E> & UnknownSafeEnum> E preserveUnknown(E current, E requested) {
        if (current != null && current.isUnknown()
                && (requested == null || requested.isUnknown())) {
            return current;
        }
        return requested;
    }

    private static boolean isUnknown(UnknownSafeEnum value) {
        return value != null && value.isUnknown();
    }

    private static boolean becameUnknown(UnknownSafeEnum loaded, UnknownSafeEnum current) {
        return !isUnknown(loaded) && isUnknown(current);
    }

    public boolean withinPeriod(int day) {
        return withinPeriod(startDate, endDate, day);
    }

    public static boolean withinPeriod(LocalDate startDate, LocalDate endDate, int day) {
        return day <= ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            return null;
        }
        return trimmedValue;
    }
}
