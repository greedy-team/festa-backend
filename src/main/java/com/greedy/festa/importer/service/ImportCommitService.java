package com.greedy.festa.importer.service;

import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.entity.Lineup;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.festival.entity.ExternalVisitorPolicy;
import com.greedy.festa.festival.entity.Festival;
import com.greedy.festa.festival.entity.FestivalHashtag;
import com.greedy.festa.festival.entity.TicketType;
import com.greedy.festa.festival.entity.VerificationMethod;
import com.greedy.festa.festival.repository.FestivalHashtagRepository;
import com.greedy.festa.festival.repository.FestivalRepository;
import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.host.entity.Host;
import com.greedy.festa.host.repository.HostRepository;
import com.greedy.festa.importer.dto.ImportCommitRequest;
import com.greedy.festa.importer.dto.ImportCommitResponse;
import com.greedy.festa.importer.dto.ImportCommitResult;
import com.greedy.festa.importer.dto.ImportCommitSectionResult;
import com.greedy.festa.importer.entity.ImportBatch;
import com.greedy.festa.importer.entity.ImportCommitAction;
import com.greedy.festa.importer.entity.ImportCommitPayload;
import com.greedy.festa.importer.entity.ImportCommitRow;
import com.greedy.festa.importer.entity.ImportCommitSection;
import com.greedy.festa.importer.entity.ImportConflictPolicy;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ArtistMatchStatus;
import com.greedy.festa.importer.model.ImportPreviewAction;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.StoredImportPreview;
import com.greedy.festa.importer.model.StoredPreviewRow;
import com.greedy.festa.importer.repository.ImportBatchRepository;
import com.greedy.festa.importer.repository.ImportCommitRowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportCommitService {

    private final ImportBatchRepository importBatchRepository;
    private final ImportCommitRowRepository importCommitRowRepository;
    private final HostRepository hostRepository;
    private final ArtistRepository artistRepository;
    private final ArtistAliasRepository artistAliasRepository;
    private final FestivalRepository festivalRepository;
    private final FestivalHashtagRepository festivalHashtagRepository;
    private final LineupRepository lineupRepository;
    private final PreviewJsonCodec previewJsonCodec;
    private final Clock clock;

    @Transactional
    public ImportCommitResponse commit(Long importId, ImportCommitRequest request) {
        ImportBatch batch = importBatchRepository.findByIdForUpdate(importId)
                .orElseThrow(() -> error(ImportErrorCode.IMPORT_NOT_FOUND));
        Instant committedAt = clock.instant();
        validateBatch(batch, committedAt);
        StoredImportPreview preview = previewJsonCodec.deserialize(batch.getPreview());
        List<StoredPreviewRow> selected = selectRows(preview.rows(), request);
        validateSelectedRows(preview.rows(), selected);

        CurrentState state = loadCurrentState(selected);
        validateCurrentState(selected, state);

        Execution execution = new Execution();
        commitArtists(selected, state, execution);
        commitFestivals(selected, state, execution, committedAt);
        commitLineups(selected, state, execution);
        saveAuditRows(batch, selected, execution, committedAt);
        batch.commit(committedAt);

        return response(batch.getId(), committedAt, execution);
    }

    private void validateBatch(ImportBatch batch, Instant now) {
        if (batch.getCommittedAt() != null) {
            throw error(ImportErrorCode.IMPORT_ALREADY_COMMITTED);
        }
        if (!batch.getExpiresAt().isAfter(now)) {
            throw error(ImportErrorCode.IMPORT_EXPIRED);
        }
        if (batch.getPreview() == null || batch.getPreview().isBlank()) {
            throw error(ImportErrorCode.IMPORT_INVALID_PREVIEW);
        }
    }

    private List<StoredPreviewRow> selectRows(
            List<StoredPreviewRow> rows, ImportCommitRequest request
    ) {
        if (rows == null) {
            throw error(ImportErrorCode.IMPORT_INVALID_PREVIEW);
        }
        if (request == null || request.lines() == null) {
            return List.copyOf(rows);
        }
        Map<ImportSection, Set<Integer>> requested = new EnumMap<>(ImportSection.class);
        request.lines().forEach((key, values) -> {
            ImportSection section = key == null ? null
                    : ImportSection.fromPath(key.toLowerCase(Locale.ROOT));
            if (section == null || values == null || values.stream().anyMatch(Objects::isNull)) {
                throw error(ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
            }
            requested.computeIfAbsent(section, ignored -> new LinkedHashSet<>()).addAll(values);
        });

        Set<RowKey> available = rows.stream()
                .map(row -> new RowKey(row.section(), row.line()))
                .collect(Collectors.toSet());
        requested.forEach((section, lines) -> lines.forEach(line -> {
            if (!available.contains(new RowKey(section, line))) {
                throw error(ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
            }
        }));
        return rows.stream().filter(row -> requested
                .getOrDefault(row.section(), Set.of()).contains(row.line())).toList();
    }

    private void validateSelectedRows(
            List<StoredPreviewRow> allRows, List<StoredPreviewRow> selected
    ) {
        if (selected.isEmpty()) {
            throw error(ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
        }
        if (selected.stream().anyMatch(this::uncommittable)) {
            throw error(ImportErrorCode.IMPORT_UNCOMMITTABLE);
        }
        validateSelectedArtistClaims(selected);
        Set<RowKey> selectedKeys = selected.stream()
                .map(row -> new RowKey(row.section(), row.line()))
                .collect(Collectors.toSet());

        Map<String, Set<RowKey>> allCommitableLineups = allRows.stream()
                .filter(row -> row.section() == ImportSection.LINEUPS)
                .filter(row -> !uncommittable(row))
                .collect(Collectors.groupingBy(StoredPreviewRow::importKey,
                        Collectors.mapping(row -> new RowKey(row.section(), row.line()), Collectors.toSet())));
        Set<String> selectedFestivalKeys = selected.stream()
                .filter(row -> row.section() == ImportSection.LINEUPS)
                .map(StoredPreviewRow::importKey).collect(Collectors.toSet());
        selectedFestivalKeys.forEach(importKey -> {
            boolean hasInvalidSibling = allRows.stream()
                    .filter(row -> row.section() == ImportSection.LINEUPS)
                    .filter(row -> Objects.equals(row.importKey(), importKey))
                    .anyMatch(this::uncommittable);
            if (hasInvalidSibling) {
                throw error(ImportErrorCode.IMPORT_UNCOMMITTABLE);
            }
            if (!selectedKeys.containsAll(allCommitableLineups.getOrDefault(importKey, Set.of()))) {
                throw error(ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
            }
        });

        Set<String> selectedNewFestivals = selected.stream()
                .filter(row -> row.section() == ImportSection.FESTIVALS)
                .filter(row -> row.action() == ImportPreviewAction.CREATE)
                .map(StoredPreviewRow::importKey).collect(Collectors.toSet());
        Map<String, List<StoredPreviewRow>> selectedNewArtists = selected.stream()
                .filter(row -> row.section() == ImportSection.ARTISTS)
                .filter(row -> row.action() == ImportPreviewAction.CREATE)
                .flatMap(row -> artistKeys(row).stream().map(key -> Map.entry(key, row)))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        selected.stream().filter(row -> row.section() == ImportSection.LINEUPS).forEach(row -> {
            if (row.matchedFestivalId() == null && !selectedNewFestivals.contains(row.importKey())) {
                throw error(ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
            }
            if (Boolean.TRUE.equals(row.revealed()) && row.matchedArtistId() == null) {
                String artistKey = text(row.normalized(), "artistCanonical");
                if (artistKey.isBlank()) {
                    artistKey = text(row.normalized(), "artistRaw");
                }
                if (selectedNewArtists.getOrDefault(artistKey, List.of()).size() != 1) {
                    throw error(ImportErrorCode.IMPORT_INVALID_LINE_SELECTION);
                }
            }
        });
    }

    private void validateSelectedArtistClaims(List<StoredPreviewRow> selected) {
        Map<String, Set<String>> ownersByName = new HashMap<>();
        selected.stream().filter(row -> row.section() == ImportSection.ARTISTS)
                .filter(row -> row.action() != ImportPreviewAction.SKIP)
                .forEach(row -> {
                    String owner = row.matchedArtistId() == null
                            ? "new:" + row.line() + ':' + row.importKey()
                            : "existing:" + row.matchedArtistId();
                    artistKeys(row).forEach(name -> ownersByName
                            .computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(owner));
                });
        if (ownersByName.values().stream().anyMatch(owners -> owners.size() > 1)) {
            throw error(ImportErrorCode.IMPORT_UNCOMMITTABLE);
        }
    }

    private boolean uncommittable(StoredPreviewRow row) {
        return row == null || row.action() == ImportPreviewAction.INVALID
                || row.errors() == null
                || row.errors().stream().anyMatch(problem -> problem.blocker())
                || row.artistMatchStatus() == ArtistMatchStatus.UNRESOLVED;
    }

    private CurrentState loadCurrentState(List<StoredPreviewRow> rows) {
        Set<Long> hostIds = ids(rows, StoredPreviewRow::matchedHostId);
        Set<Long> artistIds = ids(rows, StoredPreviewRow::matchedArtistId);
        Set<String> artistNames = new LinkedHashSet<>();
        Set<String> aliasNames = new LinkedHashSet<>();
        Set<String> festivalKeys = new LinkedHashSet<>();
        for (StoredPreviewRow row : rows) {
            if (row.section() == ImportSection.ARTISTS) {
                artistNames.add(row.importKey());
                aliasNames.add(row.importKey());
                aliasNames.addAll(strings(row.normalized().get("otherNames")));
            } else if (row.section() == ImportSection.LINEUPS) {
                String name = text(row.normalized(), "artistCanonical");
                if (!name.isBlank()) {
                    artistNames.add(name);
                    aliasNames.add(name);
                }
                festivalKeys.add(row.importKey());
            } else if (row.section() == ImportSection.FESTIVALS) {
                festivalKeys.add(row.importKey());
            }
        }
        artistNames.addAll(aliasNames);
        List<ArtistAlias> aliases = aliasNames.isEmpty() ? List.of()
                : artistAliasRepository.findAllWithArtistByNameIn(aliasNames);
        aliases.stream().map(alias -> alias.getArtist().getId()).forEach(artistIds::add);
        return new CurrentState(
                hostRepository.findAllById(hostIds).stream().collect(Collectors.toMap(Host::getId, Function.identity())),
                artistRepository.findAllById(artistIds).stream().collect(Collectors.toMap(Artist::getId, Function.identity())),
                artistNames.isEmpty() ? Map.of() : groupArtists(artistRepository.findAllByNameIn(artistNames)),
                aliases.stream().collect(Collectors.groupingBy(ArtistAlias::getName)),
                festivalKeys.isEmpty() ? Map.of() : festivalRepository.findAllByImportKeyIn(festivalKeys).stream()
                        .collect(Collectors.groupingBy(Festival::getImportKey))
        );
    }

    private void validateCurrentState(List<StoredPreviewRow> rows, CurrentState state) {
        Set<String> lineupSlots = new HashSet<>();
        for (StoredPreviewRow row : rows) {
            switch (row.section()) {
                case ARTISTS -> validateArtistState(row, state);
                case FESTIVALS -> validateFestivalState(row, state);
                case LINEUPS -> {
                    validateLineupState(row, state);
                    String slot = row.importKey() + '\0' + integer(row.normalized(), "day")
                            + '\0' + integer(row.normalized(), "order");
                    if (!lineupSlots.add(slot)) {
                        throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
                    }
                }
            }
        }
    }

    private void validateArtistState(StoredPreviewRow row, CurrentState state) {
        Set<Long> matchedIds = currentArtistIds(row.importKey(), state);
        if (row.action() == ImportPreviewAction.CREATE) {
            if (!matchedIds.isEmpty()) {
                throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
            }
            validateAliasOwnership(row, null, state);
            return;
        }
        if (row.matchedArtistId() == null || !matchedIds.equals(Set.of(row.matchedArtistId()))) {
            throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
        }
        validateAliasOwnership(row, row.matchedArtistId(), state);
    }

    private void validateAliasOwnership(
            StoredPreviewRow row, Long expectedArtistId, CurrentState state
    ) {
        for (String alias : strings(row.normalized().get("otherNames"))) {
            for (Artist owner : state.artistsByName().getOrDefault(alias, List.of())) {
                if (expectedArtistId == null || !owner.getId().equals(expectedArtistId)) {
                    throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
                }
            }
            for (ArtistAlias owner : state.aliasesByName().getOrDefault(alias, List.of())) {
                if (expectedArtistId == null || !owner.getArtist().getId().equals(expectedArtistId)) {
                    throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
                }
            }
        }
    }

    private void validateFestivalState(StoredPreviewRow row, CurrentState state) {
        if (row.action() != ImportPreviewAction.SKIP || row.matchedHostId() != null) {
            Host host = state.hostsById().get(row.matchedHostId());
            if (host == null || !host.getName().equals(text(row.normalized(), "hostName"))) {
                throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
            }
        }
        List<Festival> festivals = state.festivalsByKey().getOrDefault(row.importKey(), List.of());
        if (row.action() == ImportPreviewAction.CREATE) {
            if (!festivals.isEmpty()) {
                throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
            }
        } else if (row.matchedFestivalId() != null) {
            if (festivals.size() != 1 || !festivals.getFirst().getId().equals(row.matchedFestivalId())) {
                throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
            }
            if (row.action() == ImportPreviewAction.UPDATE
                    && festivals.getFirst().getPublishedAt() != null) {
                throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
            }
        }
    }

    private void validateLineupState(StoredPreviewRow row, CurrentState state) {
        List<Festival> festivals = state.festivalsByKey().getOrDefault(row.importKey(), List.of());
        if (row.matchedFestivalId() != null
                && (festivals.size() != 1 || !festivals.getFirst().getId().equals(row.matchedFestivalId()))) {
            throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
        }
        if (!festivals.isEmpty() && festivals.getFirst().getPublishedAt() != null) {
            throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
        }
        if (Boolean.TRUE.equals(row.revealed()) && row.matchedArtistId() != null) {
            String artistName = text(row.normalized(), "artistCanonical");
            if (artistName.isBlank()) {
                artistName = text(row.normalized(), "artistRaw");
            }
            if (!currentArtistIds(artistName, state).equals(Set.of(row.matchedArtistId()))) {
                throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
            }
        }
        if (integer(row.normalized(), "day") < 1 || integer(row.normalized(), "order") < 1) {
            throw error(ImportErrorCode.IMPORT_PREVIEW_STALE);
        }
    }

    private void commitArtists(
            List<StoredPreviewRow> rows, CurrentState state, Execution execution
    ) {
        for (StoredPreviewRow row : rows) {
            if (row.section() != ImportSection.ARTISTS) {
                continue;
            }
            Artist artist;
            ImportCommitAction action = action(row.action());
            if (action == ImportCommitAction.CREATE) {
                artist = artistRepository.save(Artist.builder()
                        .name(text(row.normalized(), "name"))
                        .genre(enumValue(row.normalized(), "genre", ArtistGenre.class))
                        .imageUrl(nullableText(row.normalized(), "imageUrl"))
                        .needsReview(true)
                        .build());
            } else {
                artist = state.artistsById().get(row.matchedArtistId());
                if (action == ImportCommitAction.UPDATE) {
                    Boolean needsReview = nullableBoolean(row.normalized(), "needsReview");
                    artist.updateFromImport(enumValue(row.normalized(), "genre", ArtistGenre.class),
                            nullableText(row.normalized(), "imageUrl"),
                            needsReview == null ? artist.isNeedsReview() : needsReview);
                }
            }
            if (action != ImportCommitAction.SKIP) {
                addAliases(artist, strings(row.normalized().get("otherNames")), state);
            }
            execution.artistsByKey.put(row.importKey(), artist);
            if (action == ImportCommitAction.CREATE) {
                strings(row.normalized().get("otherNames"))
                        .forEach(alias -> execution.artistsByKey.put(alias, artist));
            }
            RowKey rowKey = RowKey.from(row);
            execution.artistByRow.put(rowKey, artist);
            execution.actions.put(rowKey, action);
            execution.count(ImportSection.ARTISTS, action);
        }
    }

    private void addAliases(Artist artist, List<String> aliases, CurrentState state) {
        Set<String> existing = state.aliasesByName().entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(alias -> alias.getArtist().getId().equals(artist.getId())))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        List<ArtistAlias> additions = aliases.stream().filter(alias -> !existing.contains(alias))
                .map(alias -> ArtistAlias.builder().artist(artist).name(alias).build()).toList();
        artistAliasRepository.saveAll(additions);
    }

    private void commitFestivals(
            List<StoredPreviewRow> rows, CurrentState state, Execution execution, Instant committedAt
    ) {
        for (StoredPreviewRow row : rows) {
            if (row.section() != ImportSection.FESTIVALS) {
                continue;
            }
            Host host = state.hostsById().get(row.matchedHostId());
            Festival festival = row.matchedFestivalId() == null ? null
                    : first(state.festivalsByKey().get(row.importKey()));
            ImportCommitAction action = action(row.action());
            if (action == ImportCommitAction.CREATE) {
                festival = festivalRepository.save(buildFestival(row, host, committedAt));
                execution.createdFestivalIds.add(festival.getId());
                replaceHashtags(festival, strings(row.normalized().get("hashtags")));
            } else if (action == ImportCommitAction.UPDATE) {
                updateFestival(festival, row, host, committedAt);
                List<String> hashtags = strings(row.normalized().get("hashtags"));
                if (!hashtags.isEmpty()) {
                    replaceHashtags(festival, hashtags);
                }
            }
            execution.festivalsByKey.put(row.importKey(), festival);
            RowKey rowKey = RowKey.from(row);
            execution.festivalByRow.put(rowKey, festival);
            execution.actions.put(rowKey, action);
            execution.count(ImportSection.FESTIVALS, action);
        }
    }

    private Festival buildFestival(StoredPreviewRow row, Host host, Instant committedAt) {
        return Festival.builder()
                .host(host).importKey(row.importKey()).name(text(row.normalized(), "name"))
                .startDate(date(row.normalized(), "startDate"))
                .endDate(date(row.normalized(), "endDate"))
                .posterUrl(nullableText(row.normalized(), "posterUrl"))
                .description(nullableText(row.normalized(), "description"))
                .venueName(nullableText(row.normalized(), "venueName"))
                .latitude(nullableDouble(row.normalized(), "latitude"))
                .longitude(nullableDouble(row.normalized(), "longitude"))
                .externalVisitor(enumValue(row.normalized(), "externalVisitorPolicy", ExternalVisitorPolicy.class))
                .verification(enumValue(row.normalized(), "verificationMethod", VerificationMethod.class))
                .ticketType(enumValue(row.normalized(), "ticketType", TicketType.class))
                .ticketOpenAt(instant(row.normalized(), "ticketOpenAt"))
                .admissionRaw(nullableText(row.normalized(), "admissionRaw"))
                .instagramUrl(nullableText(row.normalized(), "instagramUrl"))
                .discovery(nullableText(row.normalized(), "discovery"))
                .crawlFlag(nullableText(row.normalized(), "flag"))
                .sourceUrl(nullableText(row.normalized(), "sourceUrl"))
                .importedAt(committedAt).build();
    }

    private void updateFestival(Festival festival, StoredPreviewRow row, Host host, Instant committedAt) {
        festival.updateFromImport(host, text(row.normalized(), "name"),
                date(row.normalized(), "startDate"), date(row.normalized(), "endDate"),
                nullableText(row.normalized(), "posterUrl"), nullableText(row.normalized(), "description"),
                nullableText(row.normalized(), "venueName"),
                nullableDouble(row.normalized(), "latitude"), nullableDouble(row.normalized(), "longitude"),
                enumValue(row.normalized(), "externalVisitorPolicy", ExternalVisitorPolicy.class),
                enumValue(row.normalized(), "verificationMethod", VerificationMethod.class),
                enumValue(row.normalized(), "ticketType", TicketType.class),
                instant(row.normalized(), "ticketOpenAt"), nullableText(row.normalized(), "admissionRaw"),
                nullableText(row.normalized(), "instagramUrl"), nullableText(row.normalized(), "discovery"),
                nullableText(row.normalized(), "flag"), nullableText(row.normalized(), "sourceUrl"), committedAt);
    }

    private void replaceHashtags(Festival festival, List<String> hashtags) {
        if (festival.getId() != null) {
            festivalHashtagRepository.deleteAllByFestivalId(festival.getId());
        }
        festivalHashtagRepository.saveAll(new LinkedHashSet<>(hashtags).stream()
                .map(tag -> new FestivalHashtag(festival, tag)).toList());
    }

    private void commitLineups(
            List<StoredPreviewRow> rows, CurrentState state, Execution execution
    ) {
        Map<String, List<StoredPreviewRow>> grouped = rows.stream()
                .filter(row -> row.section() == ImportSection.LINEUPS)
                .collect(Collectors.groupingBy(StoredPreviewRow::importKey, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<StoredPreviewRow>> entry : grouped.entrySet()) {
            Festival festival = execution.festivalsByKey.get(entry.getKey());
            if (festival == null) {
                festival = first(state.festivalsByKey().get(entry.getKey()));
            }
            if (skipLineupGroup(entry.getValue())) {
                for (StoredPreviewRow row : entry.getValue()) {
                    Artist artist = resolveLineupArtist(row, state, execution);
                    RowKey rowKey = RowKey.from(row);
                    execution.lineupFestivalByRow.put(rowKey, festival);
                    execution.artistByRow.put(rowKey, artist);
                    execution.actions.put(rowKey, ImportCommitAction.SKIP);
                    execution.count(ImportSection.LINEUPS, ImportCommitAction.SKIP);
                }
                continue;
            }
            lineupRepository.deleteAllByFestivalId(festival.getId());
            for (StoredPreviewRow row : entry.getValue()) {
                Artist artist = resolveLineupArtist(row, state, execution);
                lineupRepository.save(Lineup.builder().festival(festival).artist(artist)
                        .day(integer(row.normalized(), "day"))
                        .displayOrder(integer(row.normalized(), "order")).build());
                RowKey rowKey = RowKey.from(row);
                execution.lineupFestivalByRow.put(rowKey, festival);
                execution.artistByRow.put(rowKey, artist);
                execution.actions.put(rowKey, ImportCommitAction.CREATE);
                execution.count(ImportSection.LINEUPS, ImportCommitAction.CREATE);
            }
        }
    }

    private boolean skipLineupGroup(List<StoredPreviewRow> rows) {
        return rows.stream().anyMatch(row -> row.conflictPolicy() == ImportConflictPolicy.SKIP
                && row.matchedFestivalId() != null);
    }

    private Artist resolveLineupArtist(StoredPreviewRow row, CurrentState state, Execution execution) {
        if (Boolean.FALSE.equals(row.revealed())) {
            return null;
        }
        if (row.matchedArtistId() != null) {
            return state.artistsById().get(row.matchedArtistId());
        }
        String key = text(row.normalized(), "artistCanonical");
        if (key.isBlank()) {
            key = text(row.normalized(), "artistRaw");
        }
        Artist artist = execution.artistsByKey.get(key);
        if (artist == null) {
            throw new IllegalStateException("검증을 통과한 Lineup의 Artist 의존성을 찾을 수 없습니다");
        }
        return artist;
    }

    private void saveAuditRows(
            ImportBatch batch, List<StoredPreviewRow> rows,
            Execution execution, Instant committedAt
    ) {
        List<ImportCommitRow> audits = rows.stream().map(row -> {
            RowKey rowKey = RowKey.from(row);
            return ImportCommitRow.builder()
                    .batch(batch).section(ImportCommitSection.valueOf(row.section().name()))
                    .line(row.line()).importKey(row.importKey()).action(execution.actions.get(rowKey))
                    .festival(row.section() == ImportSection.LINEUPS
                            ? execution.lineupFestivalByRow.get(rowKey) : execution.festivalByRow.get(rowKey))
                    .artist(execution.artistByRow.get(rowKey))
                    .payload(payload(row.payload())).committedAt(committedAt).build();
        }).toList();
        importCommitRowRepository.saveAll(audits);
    }

    private ImportCommitPayload payload(Map<String, String> value) {
        return new ImportCommitPayload(
                value.get("import_key"), value.get("host_name"), value.get("name"),
                optionalDate(value.get("start_date")), optionalDate(value.get("end_date")),
                value.get("venue_name"), optionalDouble(value.get("latitude")), optionalDouble(value.get("longitude")),
                value.get("poster_url"), payloadList(value.get("image_urls")),
                value.get("description"), payloadList(value.get("hashtags")),
                value.get("external_visitor_policy"), value.get("verification_method"),
                value.get("ticket_type"), value.get("ticket_open_at"), value.get("admission_raw"),
                value.get("source_url"), value.get("discovery"), value.get("flag"),
                value.get("instagram_url"), optionalInteger(value.get("day")),
                optionalInteger(value.get("order")), value.get("artist_raw"),
                value.get("artist_canonical"), optionalBoolean(value.get("revealed")),
                payloadList(value.get("other_names")), value.get("genre"), value.get("image_url"),
                optionalBoolean(value.get("needs_review")));
    }

    private ImportCommitResponse response(Long importId, Instant committedAt, Execution execution) {
        execution.createdFestivalIds.sort(Comparator.naturalOrder());
        return new ImportCommitResponse(importId, committedAt, new ImportCommitResult(
                execution.result(ImportSection.ARTISTS),
                execution.result(ImportSection.FESTIVALS),
                execution.result(ImportSection.LINEUPS)), List.copyOf(execution.createdFestivalIds));
    }

    private Set<Long> currentArtistIds(String name, CurrentState state) {
        Set<Long> ids = new LinkedHashSet<>();
        state.artistsByName().getOrDefault(name, List.of()).stream().map(Artist::getId).forEach(ids::add);
        state.aliasesByName().getOrDefault(name, List.of()).stream()
                .map(alias -> alias.getArtist().getId()).forEach(ids::add);
        return ids;
    }

    private Map<String, List<Artist>> groupArtists(List<Artist> artists) {
        return artists.stream().collect(Collectors.groupingBy(Artist::getName));
    }

    private <T> Set<Long> ids(List<StoredPreviewRow> rows, Function<StoredPreviewRow, Long> extractor) {
        return rows.stream().map(extractor).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Festival first(List<Festival> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private ImportCommitAction action(ImportPreviewAction action) {
        return switch (action) {
            case CREATE -> ImportCommitAction.CREATE;
            case UPDATE -> ImportCommitAction.UPDATE;
            case SKIP -> ImportCommitAction.SKIP;
            case INVALID -> throw error(ImportErrorCode.IMPORT_UNCOMMITTABLE);
        };
    }

    private String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private String nullableText(Map<String, Object> map, String key) {
        String value = text(map, key);
        return value.isBlank() ? null : value;
    }

    private int integer(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private Double nullableDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.doubleValue() : Double.valueOf(value.toString());
    }

    private boolean booleanValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Boolean nullableBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value instanceof Boolean bool ? bool : Boolean.valueOf(value.toString());
    }

    private LocalDate date(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof LocalDate localDate ? localDate : LocalDate.parse(value.toString());
    }

    private Instant instant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value instanceof Instant time ? time : Instant.parse(value.toString());
    }

    private <E extends Enum<E>> E enumValue(Map<String, Object> map, String key, Class<E> type) {
        String value = text(map, key);
        return value.isBlank() ? null : Enum.valueOf(type, value);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().filter(Objects::nonNull).map(Object::toString)
                .map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
    }

    private List<String> payloadList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\|", -1));
    }

    private Set<String> artistKeys(StoredPreviewRow row) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(row.importKey());
        keys.addAll(strings(row.normalized().get("otherNames")));
        return keys;
    }

    private LocalDate optionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Integer optionalInteger(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
    }

    private Double optionalDouble(String value) {
        return value == null || value.isBlank() ? null : Double.valueOf(value.trim());
    }

    private Boolean optionalBoolean(String value) {
        return value == null || value.isBlank() ? null : Boolean.valueOf(value.trim());
    }

    private FestaException error(ImportErrorCode errorCode) {
        return new FestaException(errorCode);
    }

    private record RowKey(ImportSection section, int line) {
        private static RowKey from(StoredPreviewRow row) {
            return new RowKey(row.section(), row.line());
        }
    }

    private record CurrentState(
            Map<Long, Host> hostsById,
            Map<Long, Artist> artistsById,
            Map<String, List<Artist>> artistsByName,
            Map<String, List<ArtistAlias>> aliasesByName,
            Map<String, List<Festival>> festivalsByKey
    ) {
    }

    private static final class Execution {
        private final Map<String, Artist> artistsByKey = new HashMap<>();
        private final Map<String, Festival> festivalsByKey = new HashMap<>();
        private final Map<RowKey, Artist> artistByRow = new HashMap<>();
        private final Map<RowKey, Festival> festivalByRow = new HashMap<>();
        private final Map<RowKey, Festival> lineupFestivalByRow = new HashMap<>();
        private final Map<RowKey, ImportCommitAction> actions = new HashMap<>();
        private final Map<ImportSection, int[]> counts = new EnumMap<>(ImportSection.class);
        private final List<Long> createdFestivalIds = new ArrayList<>();

        private void count(ImportSection section, ImportCommitAction action) {
            int[] value = counts.computeIfAbsent(section, ignored -> new int[3]);
            switch (action) {
                case CREATE -> value[0]++;
                case UPDATE -> value[1]++;
                case SKIP -> value[2]++;
            }
        }

        private ImportCommitSectionResult result(ImportSection section) {
            int[] value = counts.getOrDefault(section, new int[3]);
            return new ImportCommitSectionResult(value[0], value[1], value[2], 0);
        }
    }
}
