package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistCreateRequest;
import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.ArtistSortType;
import com.greedy.festa.artist.dto.ArtistUpdateRequest;
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
public class ArtistAdminService {

    private static final int MAX_NAME_LENGTH = 100;

    private final ArtistRepository artistRepository;
    private final ArtistAliasRepository artistAliasRepository;

    @Transactional
    public ArtistResponse create(ArtistCreateRequest request) {
        String name = blankToNull(request.name());
        validateName(name);
        List<String> otherNames = List.of();
        if (request.otherNames() != null) {
            otherNames = normalizeAliases(request.otherNames(), name);
        }
        validateAlias(otherNames);

        Artist artist = artistRepository.save(Artist.builder()
                .name(name)
                .genre(request.genre())
                .instagramUrl(blankToNull(request.instagramUrl()))
                .needsReview(false)
                .imageUrl(null).build());

        artistAliasRepository.saveAll(otherNames.stream()
                .map(artistName -> ArtistAlias.builder()
                        .artist(artist)
                        .name(artistName)
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

    @Transactional(readOnly = true)
    public ArtistResponse findOne(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));
        List<String> aliases = artistAliasRepository.findByArtistId(id).stream()
                .map(ArtistAlias::getName)
                .toList();
        return ArtistResponse.of(artist, aliases, artistRepository.countAppearancesByArtistId(id));
    }

    @Transactional
    public ArtistResponse update(Long id, ArtistUpdateRequest request) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));

        if (!request.isNamePresent() || request.name() == null) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_NAME);
        }
        if (!request.isInstagramUrlPresent() || request.instagramUrl() == null) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_INSTAGRAM_URL);
        }
        String name = request.name().trim();
        validateNameForUpdate(name, id);
        if (request.otherNames() == null) {
            artistAliasRepository.deleteByArtistIdAndName(id, name);
        }

        artist.update(name, request.genre(), null, request.needsReview());
        artist.changeInstagramUrl(blankToNull(request.instagramUrl()));

        List<String> aliasNames;
        if (request.otherNames() != null) {
            aliasNames = normalizeAliases(request.otherNames(), artist.getName());
            replaceAliases(artist, aliasNames);
        }
        else {
            aliasNames = artistAliasRepository.findByArtistId(artist.getId()).stream()
                    .map(ArtistAlias::getName)
                    .toList();
        }

        return ArtistResponse.of(
                artist,
                aliasNames,
                artistRepository.countAppearancesByArtistId(id)
        );
    }

    @Transactional
    public void delete(Long id) {
        artistRepository.findById(id)
                .orElseThrow(() -> new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND));

        if (artistRepository.countLineupsByArtistId(id) > 0) {
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

        if (artistRepository.existsByName(name)
                || artistAliasRepository.existsByName(name)) {
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

    private void validateNameForUpdate(String name, Long id) {
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_NAME);
        }

        if (artistRepository.existsByNameAndIdNot(name, id)
            || artistAliasRepository.existsByNameAndArtistIdNot(name, id)) {
            throw new FestaException(ArtistErrorCode.ARTIST_DUPLICATE_NAME);
        }
    }

    private void validateAliasesForUpdate(List<String> aliasesToAdd, Long id) {
        boolean tooLong = aliasesToAdd.stream()
                .anyMatch(otherName -> otherName.length() > MAX_NAME_LENGTH);

        if (tooLong) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_ALIAS);
        }

        boolean taken = aliasesToAdd.stream()
                .anyMatch(otherName ->
                        artistRepository.existsByNameAndIdNot(otherName, id)
                                || artistAliasRepository.existsByNameAndArtistIdNot(otherName, id));

        if (taken) {
            throw new FestaException(ArtistErrorCode.ARTIST_DUPLICATE_ALIAS);
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            return null;
        }
        return trimmedValue;
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

    private void replaceAliases(Artist artist, List<String> target) {
        List<ArtistAlias> currentAliases = artistAliasRepository.findByArtistId(artist.getId());
        List<String> currentAliasesNames = currentAliases.stream()
                .map(ArtistAlias::getName)
                .toList();

        List<ArtistAlias> aliasesToRemove = currentAliases.stream()
                .filter(alias -> !target.contains(alias.getName()))
                .toList();

        List<String> aliasesToAdd = target.stream()
                .filter(otherName -> !currentAliasesNames.contains(otherName))
                .toList();


        validateAliasesForUpdate(aliasesToAdd, artist.getId());

        artistAliasRepository.deleteAll(aliasesToRemove);
        artistAliasRepository.saveAll(aliasesToAdd.stream()
                .map(name -> ArtistAlias.builder()
                        .artist(artist)
                        .name(name)
                        .build())
                .toList());
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
