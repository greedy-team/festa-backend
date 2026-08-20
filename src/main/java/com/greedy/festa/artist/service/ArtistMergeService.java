package com.greedy.festa.artist.service;

import com.greedy.festa.artist.dto.ArtistMergeRequest;
import com.greedy.festa.artist.dto.ArtistMergeResponse;
import com.greedy.festa.artist.entity.Artist;
import com.greedy.festa.artist.entity.ArtistAlias;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.repository.ArtistAliasRepository;
import com.greedy.festa.artist.repository.ArtistRepository;
import com.greedy.festa.artist.repository.LineupRepository;
import com.greedy.festa.global.exception.FestaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ArtistMergeService {

    private final ArtistRepository artistRepository;
    private final ArtistAliasRepository artistAliasRepository;
    private final LineupRepository lineupRepository;

    @Transactional
    public ArtistMergeResponse merge(ArtistMergeRequest request) {
        List<Long> sourceIds = normalizeSourceIds(request.sourceIds());
        validateSourceIds(sourceIds);
        validateTargetNotInSources(request.targetId(), sourceIds);
        Artist target = validateArtistsExist(request.targetId(), sourceIds);

        List<String> absorbedNames = collectAbsorbedNames(sourceIds, request.keepAliases());
        int movedAppearances = lineupRepository.reassignArtist(target, sourceIds);
        int removedDuplicates = lineupRepository.removeDuplicates(target);

        artistAliasRepository.deleteByArtistIdIn(sourceIds);
        artistAliasRepository.flush();
        artistRepository.deleteAllById(sourceIds);
        artistRepository.flush();

        Artist mergedArtist = artistRepository.findById(request.targetId())
                .orElseThrow();
        addAliases(mergedArtist, absorbedNames);
        mergedArtist.markNeedsReview();

        return ArtistMergeResponse.of(
                mergedArtist, sourceIds.size(), movedAppearances, removedDuplicates,
                artistAliasRepository.findByArtistId(mergedArtist.getId()).stream().map(ArtistAlias::getName).toList()
        );
    }

    private List<String> collectAbsorbedNames(List<Long> sourceIds, boolean keepAliases) {
        if (!keepAliases) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        names.addAll(artistRepository.findAllById(sourceIds).stream()
                .map(Artist::getName)
                .toList());
        names.addAll(artistAliasRepository.findByArtistIdIn(sourceIds).stream()
                .map(ArtistAlias::getName)
                .toList());

        return names;
    }

    private void addAliases(Artist target, List<String> absorbedNames) {
        List<ArtistAlias> aliases = absorbedNames.stream()
                .distinct()
                .filter(name -> !name.equals(target.getName()))
                .filter(name -> !artistAliasRepository.existsByName(name))
                .filter(name -> !artistRepository.existsByName(name))
                .map(name -> ArtistAlias.builder().artist(target).name(name).build())
                .toList();

        artistAliasRepository.saveAll(aliases);
    }

    private List<Long> normalizeSourceIds(List<Long> sourceIds) {
        if (sourceIds == null) {
            return List.of();
        }

        return sourceIds.stream()
                .distinct()
                .toList();
    }

    private void validateTargetNotInSources(Long targetId, List<Long> sourceIds) {
        if (sourceIds.contains(targetId)) {
            throw new FestaException(ArtistErrorCode.ARTIST_SELF_MERGE);
        }
    }

    private void validateSourceIds(List<Long> sourceIds) {
        if (sourceIds.isEmpty() || sourceIds.size() > 10) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_SOURCE_IDS);
        }
    }

    private Artist validateArtistsExist(Long targetId, List<Long> sourceIds) {
        List<Long> ids = Stream.concat(Stream.of(targetId), sourceIds.stream()).toList();
        List<Artist> artists = artistRepository.findAllById(ids);

        if (artists.size() != ids.size()) {
            throw new FestaException(ArtistErrorCode.ARTIST_NOT_FOUND);
        }

        return artists.stream()
                .filter(artist -> artist.getId().equals(targetId))
                .findFirst()
                .orElseThrow();
    }
}
