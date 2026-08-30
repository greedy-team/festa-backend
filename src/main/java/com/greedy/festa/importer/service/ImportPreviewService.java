package com.greedy.festa.importer.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.config.ClockConfig;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.importer.dto.ImportPreviewResponse;
import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportBatchType;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ArtistMatchStatus;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.ParsedCsvRow;
import com.greedy.festa.importer.model.PreviewProblem;
import com.greedy.festa.importer.model.StoredImportPreview;
import com.greedy.festa.importer.model.StoredPreviewRow;
import com.greedy.festa.importer.parser.ImportCsvParser;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportPreviewService {

    private static final Set<String> FLAGS = Set.of(
            "OK", "FETCH_FAILED", "EMPTY_BODY", "EXTRACT_FAILED", "MISMATCH",
            "NO_CANDIDATE", "NO_SOURCE");
    private static final Set<String> DISCOVERIES = Set.of("MANUAL", "SITEMAP", "SEARCH", "PASTED");
    private static final double MIN_LATITUDE = -90;
    private static final double MAX_LATITUDE = 90;
    private static final double MIN_LONGITUDE = -180;
    private static final double MAX_LONGITUDE = 180;
    private static final int MIN_KOREA_LATITUDE = 33;
    private static final int MAX_KOREA_LATITUDE = 39;
    private static final int MIN_KOREA_LONGITUDE = 124;
    private static final int MAX_KOREA_LONGITUDE = 132;

    private final ImportCsvParser csvParser;
    private final HostRepository hostRepository;
    private final ArtistRepository artistRepository;
    private final ArtistAliasRepository artistAliasRepository;
    private final FestivalRepository festivalRepository;
    private final ImportBatchRepository importBatchRepository;
    private final PreviewJsonCodec previewJsonCodec;

    @Transactional
    public ImportPreviewResponse previewBundle(
            MultipartFile festivals,
            MultipartFile lineups,
            MultipartFile artists,
            ImportConflictPolicy onConflict,
            Instant uploadedAt
    ) {
        if (festivals == null || lineups == null) {
            throw new FestaException(ImportErrorCode.IMPORT_MISSING_FILE);
        }
        EnumMap<ImportSection, MultipartFile> files = new EnumMap<>(ImportSection.class);
        files.put(ImportSection.FESTIVALS, festivals);
        files.put(ImportSection.LINEUPS, lineups);
        if (artists != null && !artists.isEmpty()) {
            files.put(ImportSection.ARTISTS, artists);
        }
        return preview(ImportBatchType.BUNDLE, files, onConflict, uploadedAt);
    }

    @Transactional
    public ImportPreviewResponse previewSingle(
            ImportSection section,
            MultipartFile file,
            ImportConflictPolicy onConflict,
            Instant uploadedAt
    ) {
        if (section == null) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_TYPE);
        }
        if (file == null) {
            throw new FestaException(ImportErrorCode.IMPORT_MISSING_FILE);
        }
        EnumMap<ImportSection, MultipartFile> files = new EnumMap<>(ImportSection.class);
        files.put(section, file);
        return preview(section.batchType(), files, onConflict, uploadedAt);
    }

    private ImportPreviewResponse preview(
            ImportBatchType type,
            Map<ImportSection, MultipartFile> files,
            ImportConflictPolicy onConflict,
            Instant uploadedAt
    ) {
        ImportConflictPolicy policy = onConflict;
        EnumMap<ImportSection, List<ParsedCsvRow>> parsed = new EnumMap<>(ImportSection.class);
        files.forEach((section, file) -> parsed.put(section, csvParser.parse(file, section)));

        Lookup lookup = loadLookup(parsed);
        Set<String> duplicateFestivalKeys = duplicateFestivalKeys(parsed.get(ImportSection.FESTIVALS));
        Set<String> duplicateLineupSlots = duplicateLineupSlots(parsed.get(ImportSection.LINEUPS));
        Set<Integer> conflictingArtistLines = conflictingArtistLines(parsed.get(ImportSection.ARTISTS));
        List<StoredPreviewRow> rows = new ArrayList<>();

        List<StoredPreviewRow> festivalRows = parsed.getOrDefault(
                        ImportSection.FESTIVALS, List.of()).stream()
                .map(row -> festivalRow(row, lookup, duplicateFestivalKeys, policy))
                .toList();
        Map<String, List<StoredPreviewRow>> uploadedFestivals = festivalRows.stream()
                .collect(Collectors.groupingBy(StoredPreviewRow::importKey));
        rows.addAll(festivalRows);
        parsed.getOrDefault(ImportSection.LINEUPS, List.of()).forEach(row -> rows.add(lineupRow(
                row, lookup, uploadedFestivals, duplicateFestivalKeys,
                duplicateLineupSlots, policy)));
        parsed.getOrDefault(ImportSection.ARTISTS, List.of()).forEach(row -> rows.add(
                artistRow(row, lookup, conflictingArtistLines, policy)));

        StoredImportPreview stored = new StoredImportPreview(
                StoredImportPreview.CURRENT_SCHEMA_VERSION, policy, List.copyOf(rows));
        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .type(type)
                .fileNames(files.entrySet().stream().map(entry -> {
                    String original = entry.getValue().getOriginalFilename();
                    return original == null || original.isBlank()
                            ? entry.getKey().name().toLowerCase() + ".csv" : original;
                }).toList())
                .onConflict(policy)
                .preview(previewJsonCodec.serialize(stored))
                .uploadedByAdmin(null)
                .uploadedAt(uploadedAt)
                .build());
        return ImportPreviewResponse.of(batch, rows);
    }

    private Lookup loadLookup(Map<ImportSection, List<ParsedCsvRow>> parsed) {
        Set<String> hostNames = values(parsed.get(ImportSection.FESTIVALS), "host_name");
        Set<String> artistNames = new LinkedHashSet<>();
        artistNames.addAll(values(parsed.get(ImportSection.ARTISTS), "name"));
        List<ParsedCsvRow> artistRows = parsed.get(ImportSection.ARTISTS);
        if (artistRows != null) {
            artistRows.stream()
                    .flatMap(row -> pipeValues(row.values().get("other_names")).stream())
                    .forEach(artistNames::add);
        }
        artistNames.addAll(values(parsed.get(ImportSection.LINEUPS), "artist_canonical"));
        artistNames.addAll(values(parsed.get(ImportSection.LINEUPS), "artist_raw"));
        artistNames.remove("");
        Set<String> importKeys = values(parsed.get(ImportSection.FESTIVALS), "import_key");
        importKeys.addAll(values(parsed.get(ImportSection.LINEUPS), "import_key"));

        Map<String, List<Host>> hosts = hostNames.isEmpty() ? Map.of()
                : group(hostRepository.findAllByNameIn(hostNames), Host::getName);
        Map<String, List<Artist>> artists = artistNames.isEmpty() ? Map.of()
                : group(artistRepository.findAllByNameIn(artistNames), Artist::getName);
        Map<String, List<Artist>> aliases = (artistNames.isEmpty() ? List.<ArtistAlias>of()
                : artistAliasRepository.findAllWithArtistByNameIn(artistNames)).stream()
                .collect(Collectors.groupingBy(ArtistAlias::getName,
                        Collectors.mapping(ArtistAlias::getArtist, Collectors.toList())));
        Map<String, List<Festival>> festivals = importKeys.isEmpty() ? Map.of() : group(
                festivalRepository.findAllByImportKeyIn(importKeys), Festival::getImportKey);

        Set<Long> matchedArtistIds = artists.values().stream().flatMap(Collection::stream)
                .map(Artist::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        aliases.values().stream().flatMap(Collection::stream).map(Artist::getId)
                .filter(Objects::nonNull).forEach(matchedArtistIds::add);
        Map<Long, List<String>> aliasesByArtist = matchedArtistIds.isEmpty()
                ? Map.of()
                : artistAliasRepository.findAllByArtistIdIn(matchedArtistIds).stream()
                        .collect(Collectors.groupingBy(alias -> alias.getArtist().getId(),
                                Collectors.mapping(ArtistAlias::getName, Collectors.toList())));
        return new Lookup(hosts, artists, aliases, festivals, aliasesByArtist);
    }

    private StoredPreviewRow festivalRow(
            ParsedCsvRow row,
            Lookup lookup,
            Set<String> duplicateKeys,
            ImportConflictPolicy policy
    ) {
        Map<String, String> payload = row.values();
        List<PreviewProblem> errors = new ArrayList<>();
        List<PreviewProblem> warnings = new ArrayList<>();
        String importKey = trim(payload.get("import_key"));
        String hostName = trim(payload.get("host_name"));
        String name = trim(payload.get("name"));
        required(importKey, "import_key", errors);
        required(hostName, "host_name", errors);

        String flag = trim(payload.get("flag"));
        enumText(flag, FLAGS, "flag", true, errors);
        boolean successfulCrawl = "OK".equals(flag);
        if (successfulCrawl) {
            required(name, "name", errors);
        }

        List<Host> hosts = lookup.hosts().getOrDefault(hostName, List.of());
        if (successfulCrawl && hosts.isEmpty()) {
            errors.add(blocker("HOST_NOT_FOUND", "Host.name과 일치하는 주최가 없습니다"));
        } else if (successfulCrawl && hosts.size() > 1) {
            errors.add(blocker("HOST_AMBIGUOUS", "Host.name과 일치하는 주최가 여러 개입니다"));
        }
        if (duplicateKeys.contains(importKey)) {
            errors.add(blocker("DUPLICATE_IMPORT_KEY", "같은 업로드에 중복된 import_key가 있습니다"));
        }

        LocalDate startDate = successfulCrawl
                ? localDate(payload.get("start_date"), "start_date", errors) : null;
        LocalDate endDate = successfulCrawl
                ? localDate(payload.get("end_date"), "end_date", errors) : null;
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            errors.add(error("INVALID_DATE_RANGE", "end_date는 start_date보다 빠를 수 없습니다"));
        }
        Double latitude = coordinate(payload.get("latitude"), "latitude",
                MIN_LATITUDE, MAX_LATITUDE, errors);
        Double longitude = coordinate(payload.get("longitude"), "longitude",
                MIN_LONGITUDE, MAX_LONGITUDE, errors);
        coordinateOutsideKorea(latitude, "latitude",
                MIN_KOREA_LATITUDE, MAX_KOREA_LATITUDE, warnings);
        coordinateOutsideKorea(longitude, "longitude",
                MIN_KOREA_LONGITUDE, MAX_KOREA_LONGITUDE, warnings);

        String discovery = trim(payload.get("discovery"));
        if (successfulCrawl) {
            enumText(discovery, DISCOVERIES, "discovery", true, errors);
        } else if (!discovery.isEmpty()) {
            enumText(discovery, DISCOVERIES, "discovery", false, errors);
        }
        enumValue(payload.get("external_visitor_policy"), ExternalVisitorPolicy.class,
                "external_visitor_policy", errors);
        enumValue(payload.get("verification_method"), VerificationMethod.class,
                "verification_method", errors);
        enumValue(payload.get("ticket_type"), TicketType.class, "ticket_type", errors);
        url(payload.get("poster_url"), "poster_url", errors);
        url(payload.get("source_url"), "source_url", errors);
        url(payload.get("instagram_url"), "instagram_url", errors);
        Instant ticketOpenAt = ticketOpenAt(payload.get("ticket_open_at"), errors);
        List<String> imageUrls = pipeValues(payload.get("image_urls"));
        imageUrls.forEach(value -> url(value, "image_urls", errors));
        if (imageUrls.size() > 1) {
            warnings.add(warning("ADDITIONAL_IMAGE_URLS_NOT_PERSISTED",
                    "대표 이미지 외 image_urls는 현재 Festival에 저장되지 않습니다"));
        }
        String posterCandidate = firstNonBlank(payload.get("poster_url"),
                imageUrls.isEmpty() ? null : imageUrls.getFirst());

        List<Festival> festivals = lookup.festivals().getOrDefault(importKey, List.of());
        if (festivals.size() > 1) {
            errors.add(blocker("FESTIVAL_AMBIGUOUS", "동일 import_key의 Festival이 여러 개입니다"));
        }
        Festival existing = festivals.size() == 1 ? festivals.getFirst() : null;
        if (successfulCrawl && existing != null && existing.getPublishedAt() != null) {
            errors.add(blocker("FESTIVAL_ALREADY_PUBLISHED",
                    "발행된 Festival은 임포트로 변경할 수 없습니다"));
        }
        ImportPreviewAction action;
        String skipReason = null;
        if (!errors.isEmpty()) {
            action = ImportPreviewAction.INVALID;
        } else if (!"OK".equals(flag)) {
            action = ImportPreviewAction.SKIP;
            skipReason = "CRAWLER_FLAG_" + flag;
        } else if (existing == null) {
            action = ImportPreviewAction.CREATE;
        } else if (policy == ImportConflictPolicy.SKIP) {
            action = ImportPreviewAction.SKIP;
            skipReason = "ON_CONFLICT_SKIP";
        } else {
            action = ImportPreviewAction.UPDATE;
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("importKey", importKey);
        normalized.put("hostName", hostName);
        normalized.put("name", keepExisting(name, existing == null ? null : existing.getName()));
        normalized.put("startDate", isoDate(startDate));
        normalized.put("endDate", isoDate(endDate));
        normalized.put("venueName", keepExisting(trim(payload.get("venue_name")),
                existing == null ? null : existing.getVenueName()));
        normalized.put("latitude", keepExisting(latitude,
                existing == null ? null : existing.getLatitude()));
        normalized.put("longitude", keepExisting(longitude,
                existing == null ? null : existing.getLongitude()));
        normalized.put("posterUrl", keepExisting(posterCandidate,
                existing == null ? null : existing.getPosterUrl()));
        normalized.put("imageUrls", imageUrls);
        normalized.put("description", keepExisting(trim(payload.get("description")),
                existing == null ? null : existing.getDescription()));
        normalized.put("hashtags", pipeValues(payload.get("hashtags")));
        normalized.put("externalVisitorPolicy", keepExisting(trim(payload.get("external_visitor_policy")),
                existing == null || existing.getExternalVisitor() == null
                        ? null : existing.getExternalVisitor().name()));
        normalized.put("verificationMethod", keepExisting(trim(payload.get("verification_method")),
                existing == null || existing.getVerification() == null
                        ? null : existing.getVerification().name()));
        normalized.put("ticketType", keepExisting(trim(payload.get("ticket_type")),
                existing == null || existing.getTicketType() == null
                        ? null : existing.getTicketType().name()));
        normalized.put("ticketOpenAt", keepExisting(isoInstant(ticketOpenAt),
                existing == null ? null : isoInstant(existing.getTicketOpenAt())));
        normalized.put("ticketOpenAtRaw", payload.get("ticket_open_at"));
        normalized.put("admissionRaw", keepExisting(trim(payload.get("admission_raw")),
                existing == null ? null : existing.getAdmissionRaw()));
        normalized.put("sourceUrl", keepExisting(trim(payload.get("source_url")),
                existing == null ? null : existing.getSourceUrl()));
        normalized.put("instagramUrl", keepExisting(trim(payload.get("instagram_url")),
                existing == null ? null : existing.getInstagramUrl()));
        normalized.put("flag", flag);
        normalized.put("discovery", discovery);
        return stored(ImportSection.FESTIVALS, row, importKey, action, normalized,
                policy,
                hosts.size() == 1 ? hosts.getFirst().getId() : null, null,
                existing == null ? null : existing.getId(), null, errors, warnings, skipReason,
                null, imageUrls, payload.get("ticket_open_at"));
    }

    private StoredPreviewRow artistRow(
            ParsedCsvRow row,
            Lookup lookup,
            Set<Integer> conflictingLines,
            ImportConflictPolicy policy
    ) {
        Map<String, String> payload = row.values();
        List<PreviewProblem> errors = new ArrayList<>();
        List<PreviewProblem> warnings = new ArrayList<>();
        String name = trim(payload.get("name"));
        required(name, "name", errors);
        ArtistMatch match = artistMatch(name, lookup);
        if (match.status() == ArtistMatchStatus.UNRESOLVED) {
            errors.add(blocker("ARTIST_UNRESOLVED", "Artist.name과 ArtistAlias.name 매칭이 모호합니다"));
        } else if (match.status() == ArtistMatchStatus.NEW) {
            warnings.add(warning("ARTIST_WILL_BE_CREATED", "후속 commit에서 신규 Artist 생성이 필요합니다"));
        }
        if (conflictingLines.contains(row.line())) {
            errors.add(blocker("ARTIST_DUPLICATE_NAME",
                    "같은 업로드에서 Artist 대표명 또는 별칭이 중복됩니다"));
        }
        enumValue(payload.get("genre"), ArtistGenre.class, "genre", errors);
        booleanValue(payload.get("needs_review"), "needs_review", true, errors);
        url(payload.get("image_url"), "image_url", errors);

        List<String> inputAliases = pipeValues(payload.get("other_names"));
        Long matchedArtistId = match.artist() == null ? null : match.artist().getId();
        if (claimsExistingAliasAsRepresentative(name, inputAliases, match, lookup)) {
            errors.add(blocker("ARTIST_DUPLICATE_NAME",
                    "Artist 대표명이 기존 Artist의 별칭과 중복됩니다"));
        }
        for (String alias : inputAliases) {
            if (ownedByAnotherArtist(alias, matchedArtistId, lookup)) {
                errors.add(blocker("ARTIST_DUPLICATE_NAME",
                        "Artist 별칭이 다른 Artist의 대표명 또는 별칭과 중복됩니다"));
                break;
            }
        }
        LinkedHashSet<String> mergedAliases = new LinkedHashSet<>();
        if (match.artist() != null && match.artist().getId() != null) {
            mergedAliases.addAll(lookup.aliasesByArtist()
                    .getOrDefault(match.artist().getId(), List.of()));
        }
        mergedAliases.addAll(inputAliases);
        mergedAliases.remove(name);

        ImportPreviewAction action;
        String skipReason = null;
        if (!errors.isEmpty()) {
            action = ImportPreviewAction.INVALID;
        } else if (match.status() == ArtistMatchStatus.NEW) {
            action = ImportPreviewAction.CREATE;
        } else if (policy == ImportConflictPolicy.SKIP) {
            action = ImportPreviewAction.SKIP;
            skipReason = "ON_CONFLICT_SKIP";
        } else {
            action = ImportPreviewAction.UPDATE;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("name", name);
        normalized.put("otherNames", List.copyOf(mergedAliases));
        normalized.put("genre", keepExisting(trim(payload.get("genre")),
                match.artist() == null || match.artist().getGenre() == null
                        ? null : match.artist().getGenre().name()));
        normalized.put("imageUrl", keepExisting(trim(payload.get("image_url")),
                match.artist() == null ? null : match.artist().getImageUrl()));
        normalized.put("needsReview", match.status() == ArtistMatchStatus.NEW
                ? true
                : booleanValue(payload.get("needs_review"),
                "needs_review", false, new ArrayList<>()));
        return stored(ImportSection.ARTISTS, row, name, action, normalized, policy, null,
                match.artist() == null ? null : match.artist().getId(), null, match.status(),
                errors, warnings, skipReason, null, List.of(), null);
    }

    private StoredPreviewRow lineupRow(
            ParsedCsvRow row,
            Lookup lookup,
            Map<String, List<StoredPreviewRow>> uploadedFestivals,
            Set<String> duplicateFestivalKeys,
            Set<String> duplicateLineupSlots,
            ImportConflictPolicy policy
    ) {
        Map<String, String> payload = row.values();
        List<PreviewProblem> errors = new ArrayList<>();
        String importKey = trim(payload.get("import_key"));
        required(importKey, "import_key", errors);
        if (duplicateFestivalKeys.contains(importKey)) {
            errors.add(blocker("DUPLICATE_IMPORT_KEY",
                    "Lineup이 참조하는 Festival import_key가 중복되어 있습니다"));
        }
        List<Festival> festivals = lookup.festivals().getOrDefault(importKey, List.of());
        List<StoredPreviewRow> parentRows = uploadedFestivals.getOrDefault(importKey, List.of());
        if (parentRows.stream().anyMatch(parent -> parent.action() == ImportPreviewAction.INVALID)) {
            errors.add(blocker("FESTIVAL_INVALID",
                    "Lineup이 참조하는 업로드 Festival이 유효하지 않습니다"));
        }
        if (festivals.stream().anyMatch(festival -> festival.getPublishedAt() != null)) {
            errors.add(blocker("FESTIVAL_ALREADY_PUBLISHED",
                    "발행된 Festival의 Lineup은 임포트로 변경할 수 없습니다"));
        }
        if (parentRows.isEmpty() && festivals.isEmpty()) {
            errors.add(blocker("FESTIVAL_NOT_FOUND", "Lineup이 참조하는 Festival이 없습니다"));
        } else if (festivals.size() > 1) {
            errors.add(blocker("FESTIVAL_AMBIGUOUS", "동일 import_key의 Festival이 여러 개입니다"));
        }
        Integer day = positiveInteger(payload.get("day"), "day", errors);
        Integer order = positiveInteger(payload.get("order"), "order", errors);
        validateLineupDay(day, parentRows, festivals, errors);
        if (duplicateLineupSlots.contains(lineupSlot(importKey, day, order))) {
            errors.add(blocker("DUPLICATE_LINEUP_POSITION",
                    "같은 import_key, day, order 조합이 중복되었습니다"));
        }
        Boolean revealed = booleanValue(payload.get("revealed"), "revealed", true, errors);
        String artistName = firstNonBlank(payload.get("artist_canonical"), payload.get("artist_raw"));
        ArtistMatch match;
        if (Boolean.FALSE.equals(revealed)) {
            match = new ArtistMatch(ArtistMatchStatus.NEW, null);
        } else {
            required(artistName, "artist_canonical", errors);
            match = artistMatch(artistName, lookup);
            if (match.status() == ArtistMatchStatus.UNRESOLVED) {
                errors.add(blocker("ARTIST_UNRESOLVED", "Artist 매칭이 모호합니다"));
            }
        }
        List<PreviewProblem> warnings = Boolean.TRUE.equals(revealed)
                && match.status() == ArtistMatchStatus.NEW
                ? List.of(warning("ARTIST_WILL_BE_CREATED",
                        "후속 commit에서 신규 Artist 생성이 필요합니다"))
                : List.of();
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("importKey", importKey);
        normalized.put("day", day);
        normalized.put("order", order);
        normalized.put("artistRaw", trim(payload.get("artist_raw")));
        normalized.put("artistCanonical", trim(payload.get("artist_canonical")));
        normalized.put("revealed", revealed);
        return stored(ImportSection.LINEUPS, row, importKey,
                errors.isEmpty() ? ImportPreviewAction.CREATE : ImportPreviewAction.INVALID,
                normalized, policy, null, match.artist() == null ? null : match.artist().getId(),
                festivals.size() == 1 ? festivals.getFirst().getId() : null,
                Boolean.FALSE.equals(revealed) ? null : match.status(), errors, warnings, null,
                revealed, List.of(), null);
    }

    private void validateLineupDay(
            Integer day,
            List<StoredPreviewRow> parentRows,
            List<Festival> festivals,
            List<PreviewProblem> errors
    ) {
        if (day == null) {
            return;
        }

        LocalDate startDate = null;
        LocalDate endDate = null;
        if (parentRows.size() == 1) {
            Map<String, Object> festival = parentRows.getFirst().normalized();
            startDate = dateValue(festival.get("startDate"));
            endDate = dateValue(festival.get("endDate"));
        } else if (parentRows.isEmpty() && festivals.size() == 1) {
            startDate = festivals.getFirst().getStartDate();
            endDate = festivals.getFirst().getEndDate();
        }

        if (startDate != null && endDate != null
                && day > ChronoUnit.DAYS.between(startDate, endDate) + 1) {
            errors.add(blocker("LINEUP_DAY_OUT_OF_RANGE",
                    "day 값은 Festival 기간을 벗어날 수 없습니다"));
        }
    }

    private LocalDate dateValue(Object value) {
        return value == null ? null : LocalDate.parse(value.toString());
    }

    private ArtistMatch artistMatch(String input, Lookup lookup) {
        String name = trim(input);
        if (name.isEmpty()) {
            return new ArtistMatch(ArtistMatchStatus.NEW, null);
        }
        List<Artist> direct = lookup.artists().getOrDefault(name, List.of());
        List<Artist> alias = lookup.aliases().getOrDefault(name, List.of());
        Set<Long> candidateIds = new LinkedHashSet<>();
        direct.stream().map(Artist::getId).forEach(candidateIds::add);
        alias.stream().map(Artist::getId).forEach(candidateIds::add);
        if (candidateIds.size() > 1 || direct.size() > 1 || alias.size() > 1) {
            return new ArtistMatch(ArtistMatchStatus.UNRESOLVED, null);
        }
        Artist matched = !direct.isEmpty() ? direct.getFirst() : alias.isEmpty() ? null : alias.getFirst();
        return matched == null
                ? new ArtistMatch(ArtistMatchStatus.NEW, null)
                : new ArtistMatch(ArtistMatchStatus.MATCHED, matched);
    }

    private boolean ownedByAnotherArtist(String name, Long expectedArtistId, Lookup lookup) {
        return lookup.artists().getOrDefault(name, List.of()).stream()
                .map(Artist::getId)
                .anyMatch(id -> expectedArtistId == null || !Objects.equals(id, expectedArtistId))
                || lookup.aliases().getOrDefault(name, List.of()).stream()
                .map(Artist::getId)
                .anyMatch(id -> expectedArtistId == null || !Objects.equals(id, expectedArtistId));
    }

    private boolean claimsExistingAliasAsRepresentative(
            String name, List<String> inputAliases, ArtistMatch match, Lookup lookup
    ) {
        if (match.status() != ArtistMatchStatus.MATCHED || match.artist() == null) {
            return false;
        }
        Long matchedArtistId = match.artist().getId();
        boolean matchesRepresentative = lookup.artists().getOrDefault(name, List.of()).stream()
                .anyMatch(artist -> Objects.equals(artist.getId(), matchedArtistId));
        if (matchesRepresentative) {
            return false;
        }
        boolean matchesAlias = lookup.aliases().getOrDefault(name, List.of()).stream()
                .anyMatch(artist -> Objects.equals(artist.getId(), matchedArtistId));
        return matchesAlias && !inputAliases.contains(match.artist().getName());
    }

    private Set<Integer> conflictingArtistLines(List<ParsedCsvRow> rows) {
        if (rows == null) {
            return Set.of();
        }
        Map<String, Set<Integer>> claims = new LinkedHashMap<>();
        for (ParsedCsvRow row : rows) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            names.add(trim(row.values().get("name")));
            names.addAll(pipeValues(row.values().get("other_names")));
            names.stream().filter(name -> !name.isBlank()).forEach(name -> claims
                    .computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(row.line()));
        }
        return claims.values().stream()
                .filter(lines -> lines.size() > 1)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private StoredPreviewRow stored(
            ImportSection section, ParsedCsvRow row, String importKey, ImportPreviewAction action,
            Map<String, Object> normalized, ImportConflictPolicy policy,
            Long hostId, Long artistId, Long festivalId,
            ArtistMatchStatus artistStatus, List<PreviewProblem> errors,
            List<PreviewProblem> warnings, String skipReason, Boolean revealed,
            List<String> imageUrls, String ticketOpenAtRaw
    ) {
        return new StoredPreviewRow(section, row.line(), importKey, action, policy,
                Collections.unmodifiableMap(new LinkedHashMap<>(normalized)),
                row.values(), hostId, artistId, festivalId, artistStatus, List.copyOf(errors),
                List.copyOf(warnings), skipReason, revealed, List.copyOf(imageUrls), ticketOpenAtRaw);
    }

    private Set<String> duplicateFestivalKeys(List<ParsedCsvRow> rows) {
        if (rows == null) {
            return Set.of();
        }
        Map<String, Long> counts = rows.stream().map(row -> trim(row.values().get("import_key")))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream().filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private Set<String> duplicateLineupSlots(List<ParsedCsvRow> rows) {
        if (rows == null) {
            return Set.of();
        }
        Map<String, Long> counts = rows.stream()
                .map(row -> lineupSlot(
                        trim(row.values().get("import_key")),
                        integerOrNull(row.values().get("day")),
                        integerOrNull(row.values().get("order"))))
                .filter(value -> value != null)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream().filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private String lineupSlot(String importKey, Integer day, Integer order) {
        return importKey.isBlank() || day == null || order == null
                ? null : importKey + "\u0000" + day + "\u0000" + order;
    }

    private Integer integerOrNull(String value) {
        try {
            return Integer.valueOf(trim(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Set<String> values(List<ParsedCsvRow> rows, String field) {
        if (rows == null) {
            return new LinkedHashSet<>();
        }
        return rows.stream().map(row -> trim(row.values().get(field)))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private <T> Map<String, List<T>> group(List<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.groupingBy(key));
    }

    private void required(String value, String field, List<PreviewProblem> errors) {
        if (value == null || value.isBlank()) {
            errors.add(error("REQUIRED_FIELD", field + " 값이 필요합니다"));
        }
    }

    private LocalDate localDate(String value, String field, List<PreviewProblem> errors) {
        if (trim(value).isEmpty()) {
            required(value, field, errors);
            return null;
        }
        try {
            return LocalDate.parse(trim(value));
        } catch (DateTimeException e) {
            errors.add(error("INVALID_DATE", field + " 형식이 올바르지 않습니다"));
            return null;
        }
    }

    private Instant ticketOpenAt(String value, List<PreviewProblem> errors) {
        String text = trim(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeException ignored) {
            try {
                return LocalDateTime.parse(text).atZone(ClockConfig.KST).toInstant();
            } catch (DateTimeException e) {
                errors.add(error("INVALID_TICKET_OPEN_AT",
                        "ticket_open_at은 ISO-8601 datetime이어야 합니다"));
                return null;
            }
        }
    }

    private <E extends Enum<E>> void enumValue(
            String value, Class<E> type, String field, List<PreviewProblem> errors
    ) {
        String text = trim(value);
        if (text.isEmpty()) {
            return;
        }
        try {
            Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            errors.add(error("INVALID_ENUM", field + " 값이 올바르지 않습니다"));
        }
    }

    private void enumText(
            String value, Set<String> allowed, String field, boolean required,
            List<PreviewProblem> errors
    ) {
        if (value.isEmpty()) {
            if (required) {
                errors.add(error("REQUIRED_FIELD", field + " 값이 필요합니다"));
            }
        } else if (!allowed.contains(value)) {
            errors.add(error("INVALID_ENUM", field + " 값이 올바르지 않습니다"));
        }
    }

    private Boolean booleanValue(
            String value, String field, boolean required, List<PreviewProblem> errors
    ) {
        String text = trim(value).toLowerCase();
        if (text.isEmpty()) {
            if (required) {
                errors.add(error("REQUIRED_FIELD", field + " 값이 필요합니다"));
            }
            return null;
        }
        if (!text.equals("true") && !text.equals("false")) {
            errors.add(error("INVALID_BOOLEAN", field + " 값이 올바르지 않습니다"));
            return null;
        }
        return Boolean.valueOf(text);
    }

    private Integer positiveInteger(String value, String field, List<PreviewProblem> errors) {
        try {
            int number = Integer.parseInt(trim(value));
            if (number < 1) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException e) {
            errors.add(error("INVALID_INTEGER", field + " 값은 1 이상의 정수여야 합니다"));
            return null;
        }
    }

    private Double coordinate(
            String value, String field, double minimum, double maximum,
            List<PreviewProblem> errors
    ) {
        String text = trim(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            double coordinate = Double.parseDouble(text);
            if (!Double.isFinite(coordinate) || coordinate < minimum || coordinate > maximum) {
                errors.add(error("COORDINATE_OUT_OF_RANGE",
                        field + " 값이 허용 범위를 벗어났습니다"));
                return null;
            }
            return coordinate;
        } catch (NumberFormatException e) {
            errors.add(error("INVALID_COORDINATE", field + " 값은 숫자여야 합니다"));
            return null;
        }
    }

    private void coordinateOutsideKorea(
            Double coordinate, String field, int minimum, int maximum,
            List<PreviewProblem> warnings
    ) {
        if (coordinate != null && (coordinate < minimum || coordinate > maximum)) {
            warnings.add(warning("COORDINATES_OUTSIDE_KOREA",
                    field + " 값이 국내 권장 범위(" + minimum + "~" + maximum + ") 밖에 있습니다"));
        }
    }

    private void url(String value, String field, List<PreviewProblem> errors) {
        String text = trim(value);
        if (text.isEmpty()) {
            return;
        }
        try {
            URI uri = new URI(text);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new URISyntaxException(text, "absolute URL required");
            }
        } catch (URISyntaxException e) {
            errors.add(error("INVALID_URL", field + " 값이 올바르지 않습니다"));
        }
    }

    private List<String> pipeValues(String value) {
        if (trim(value).isEmpty()) {
            return List.of();
        }
        return List.of(value.split("\\|", -1)).stream().map(String::trim)
                .filter(item -> !item.isEmpty()).distinct().toList();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return !trim(first).isEmpty() ? trim(first) : trim(second);
    }

    private Object keepExisting(Object incoming, Object existing) {
        return incoming == null || incoming.toString().isBlank() ? existing : incoming;
    }

    private String isoDate(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private String isoInstant(Instant value) {
        return value == null ? null : value.toString();
    }

    private PreviewProblem error(String code, String message) {
        return new PreviewProblem(code, message, false);
    }

    private PreviewProblem blocker(String code, String message) {
        return new PreviewProblem(code, message, true);
    }

    private PreviewProblem warning(String code, String message) {
        return new PreviewProblem(code, message, false);
    }

    private record Lookup(
            Map<String, List<Host>> hosts,
            Map<String, List<Artist>> artists,
            Map<String, List<Artist>> aliases,
            Map<String, List<Festival>> festivals,
            Map<Long, List<String>> aliasesByArtist
    ) {
    }

    private record ArtistMatch(ArtistMatchStatus status, Artist artist) {
    }
}
