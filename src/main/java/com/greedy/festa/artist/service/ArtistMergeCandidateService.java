package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistMatchReason;
import com.greedy.festa.artist.dto.ArtistMergeCandidateResponse;
import com.greedy.festa.artist.dto.ArtistMergeCandidateResponse.ArtistCandidate;
import com.greedy.festa.artist.dto.ArtistMergeCandidateResponse.ArtistSummary;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistAppearanceCount;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.global.exception.FestaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Service
@RequiredArgsConstructor
public class ArtistMergeCandidateService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;
    private static final double EXACT_SCORE = 0.9;
    private static final double PARTIAL_SCORE = 0.5;
    private static final double SAME_FESTIVAL_BONUS = 0.1;
    private static final double MAX_SCORE = 1.0;
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]");
    private static final int MIN_PARTIAL_LENGTH = 3;

    private static final Comparator<ArtistCandidate> BY_SIMILARITY_THEN_APPEARANCES_THEN_ID =
            Comparator.comparingDouble(ArtistCandidate::similarity).reversed()
                    .thenComparing(Comparator.comparingLong(ArtistCandidate::appearanceCount).reversed())
                    .thenComparingLong(ArtistCandidate::artistId);

    private final ArtistRepository artistRepository;
    private final ArtistAliasRepository artistAliasRepository;

    @Transactional(readOnly = true)
    public ArtistMergeCandidateResponse findAll(Long id, Long candidateLimits) {
        if (candidateLimits < MIN_LIMIT || candidateLimits > MAX_LIMIT) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_LIMIT);
        }
        Artist target = artistRepository.findById(id)
                .orElseThrow(() -> new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));

        List<Artist> others = artistRepository.findAllByIdNot(id);
        Map<Long, List<String>> aliasesByArtist = artistAliasRepository.findAll().stream()
                .collect(groupingBy(alias -> alias.getArtist().getId(),
                        mapping(ArtistAlias::getName, toList())));
        Map<Long, Long> appearanceCounts = artistRepository.countAllAppearances().stream()
                .collect(toMap(ArtistAppearanceCount::getArtistId,
                        ArtistAppearanceCount::getAppearanceCount));
        Set<Long> sameFestivalIds = Set.copyOf(artistRepository.findSameFestivalArtistIds(id));

        List<ArtistCandidate> candidates =
                collectCandidates(target, others, aliasesByArtist, appearanceCounts, sameFestivalIds);

        return new ArtistMergeCandidateResponse(
                ArtistSummary.of(target,
                        aliasesByArtist.getOrDefault(id, List.of()),
                        appearanceCounts.getOrDefault(id, 0L)),
                candidates.stream()
                        .sorted(BY_SIMILARITY_THEN_APPEARANCES_THEN_ID)
                        .limit(candidateLimits)
                        .toList());
    }

    private List<ArtistCandidate> collectCandidates(
            Artist target,
            List<Artist> others,
            Map<Long, List<String>> aliasesByArtist,
            Map<Long, Long> appearanceCounts,
            Set<Long> sameFestivalIds) {
        String targetKey = toMatchKey(target.getName());
        Set<String> targetAliasKeys = toMatchKeys(aliasesByArtist.get(target.getId()));

        List<ArtistCandidate> candidates = new ArrayList<>();
        for (Artist other : others) {
            String otherKey = toMatchKey(other.getName());
            Set<String> otherAliasKeys = toMatchKeys(aliasesByArtist.get(other.getId()));

            boolean nameExact = !targetKey.isEmpty() && targetKey.equals(otherKey);
            boolean namePartial = !nameExact
                    && Math.min(targetKey.length(), otherKey.length()) >= MIN_PARTIAL_LENGTH
                    && (targetKey.contains(otherKey) || otherKey.contains(targetKey));
            boolean aliasMatch = otherAliasKeys.contains(targetKey)
                    || targetAliasKeys.contains(otherKey)
                    || otherAliasKeys.stream().anyMatch(targetAliasKeys::contains);

            if (!nameExact && !namePartial && !aliasMatch) {
                continue;
            }

            boolean sameFestival = sameFestivalIds.contains(other.getId());

            candidates.add(ArtistCandidate.of(
                    other,
                    aliasesByArtist.getOrDefault(other.getId(), List.of()),
                    appearanceCounts.getOrDefault(other.getId(), 0L),
                    toSimilarity(nameExact || aliasMatch, sameFestival),
                    toReasons(nameExact || namePartial, aliasMatch, sameFestival)));
        }
        return candidates;
    }

    private double toSimilarity(boolean exact, boolean sameFestival) {
        double similarity = PARTIAL_SCORE;
        if (exact) {
            similarity = EXACT_SCORE;
        }
        if (sameFestival) {
            similarity = Math.min(MAX_SCORE, similarity + SAME_FESTIVAL_BONUS);
        }
        return similarity;
    }

    private List<ArtistMatchReason> toReasons(boolean nameSimilar, boolean aliasMatch, boolean sameFestival) {
        List<ArtistMatchReason> reasons = new ArrayList<>();
        if (nameSimilar) {
            reasons.add(ArtistMatchReason.NAME_SIMILAR);
        }
        if (aliasMatch) {
            reasons.add(ArtistMatchReason.ALIAS_MATCH);
        }
        if (sameFestival) {
            reasons.add(ArtistMatchReason.SAME_FESTIVAL);
        }
        return reasons;
    }

    private String toMatchKey(String name) {
        return NON_ALPHANUMERIC.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private Set<String> toMatchKeys(List<String> names) {
        if (names == null) {
            return Set.of();
        }
        return names.stream()
                .map(this::toMatchKey)
                .filter(key -> !key.isEmpty())
                .collect(toSet());
    }
}
