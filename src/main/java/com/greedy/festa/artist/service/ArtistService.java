package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistCreateRequest;
import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.ArtistSortType;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.ArtistWithAppearanceCount;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.FestaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArtistService {

    private static final int MAX_NAME_LENGTH = 100;

    private final ArtistRepository artistRepository;
    private final ArtistAliasRepository artistAliasRepository;

    @Transactional
    public ArtistResponse create(ArtistCreateRequest request) {
        validateName(request.name());
        List<String> otherNames = normalizeAliases(request.otherNames(), request.name());
        if (!otherNames.isEmpty()) {
            validateAlias(otherNames);
        }

        Artist artist = artistRepository.save(Artist.builder()
                .name(request.name())
                .genre(request.genre())
                .instagramUrl(request.instagramUrl())
                .needsReview(false)
                .imageUrl(null).build());

        List<ArtistAlias> artistAliases = artistAliasRepository.saveAll(otherNames.stream()
                .map(name -> ArtistAlias.builder()
                        .artist(artist)
                        .name(name)
                        .build())
                .toList());

        return ArtistResponse.of(artist, otherNames, 0L);
    }

    @Transactional(readOnly = true)
    public PageResponse<ArtistResponse> findAll(
            Boolean needsReview, String q, ArtistGenre genre,
            ArtistSortType sort, int page, int size
    ) {
        if (page < 0) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > 50) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        if (q != null && q.trim().length() > 50) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_QUERY);
        }

        Page<ArtistWithAppearanceCount> rows = artistRepository.findAllWithAppearanceCount(
                needsReview, genre, q, PageRequest.of(page, size, sort.toSort())
        );

        Map<Long, List<String>> aliasesByArtistId = loadAlias(rows);

        return PageResponse.from(rows.map(
                row -> ArtistResponse.of(
                        row.getArtist(),
                        aliasesByArtistId.getOrDefault(row.getArtist().getId(), List.of()),
                        row.getAppearanceCount()
                )));
    }

    @Transactional
    public void delete(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));

        if (artistRepository.countAppearancesByArtistId(id) > 0) {
            throw new FestaException(ArtistErrorCode.ARTIST_HAS_APPEARANCES);
        }

        artistAliasRepository.deleteByArtistId(id);
        artistAliasRepository.flush();
        artistRepository.deleteById(id);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_NAME);
        }

        if (artistRepository.existsByName(name)) {
            throw new FestaException(ArtistErrorCode.ARTIST_DUPLICATE_NAME);
        }
    }

    private void validateAlias(List<String> otherNames) {
        boolean tooLong = otherNames.stream()
                .anyMatch(otherName -> otherName.length() > MAX_NAME_LENGTH);

        if (tooLong) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_ALIAS);
        }

        boolean taken = otherNames.stream()
                .anyMatch(otherName ->
                        artistRepository.existsByName(otherName)
                                || artistAliasRepository.existsByName(otherName));

        if (taken) {
            throw new FestaException(ArtistErrorCode.ARTIST_DUPLICATE_ALIAS);
        }
    }

    private List<String> normalizeAliases(List<String> otherNames, String name) {
        if (otherNames == null) {
            return List.of();
        }
        return otherNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(otherName -> !otherName.isBlank())
                .filter(otherName -> !otherName.equals(name))
                .distinct()
                .toList();
    }

    private Map<Long, List<String>> loadAlias(Page<ArtistWithAppearanceCount> rows) {
        List<Long> artistIds = rows.getContent().stream()
                .map(row -> row.getArtist().getId())
                .toList();

        if (artistIds.isEmpty()) {
            return Map.of();
        }

        return artistAliasRepository.findByArtistIdIn(artistIds)
                .stream()
                .collect(Collectors.groupingBy(
                        alias -> alias.getArtist().getId(),
                        Collectors.mapping(ArtistAlias::getName, Collectors.toList()))
                );
    }
}
