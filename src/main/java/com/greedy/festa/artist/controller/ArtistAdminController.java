package com.greedy.festa.artist.controller;

import com.greedy.festa.artist.dto.ArtistCreateRequest;
import com.greedy.festa.artist.dto.ArtistMergeRequest;
import com.greedy.festa.artist.dto.ArtistMergeResponse;
import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.ArtistSortType;
import com.greedy.festa.artist.dto.ArtistUpdateRequest;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.exception.ArtistErrorCode;
import com.greedy.festa.artist.service.ArtistMergeService;
import com.greedy.festa.artist.service.ArtistService;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.FestaException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/artists")
@RequiredArgsConstructor
public class ArtistAdminController {

    private final ArtistService artistService;
    private final ArtistMergeService artistMergeService;

    @PostMapping
    public ResponseEntity<ArtistResponse> create(@RequestBody ArtistCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(artistService.create(request));
    }

    @GetMapping
    public PageResponse<ArtistResponse> findAll(
            @RequestParam(required = false) Boolean needsReview,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return artistService.findAll(needsReview, q, toGenre(genre), toSortType(sort), page, size);
    }

    @PatchMapping("/{id}")
    public ArtistResponse update(@PathVariable Long id, @RequestBody ArtistUpdateRequest request) {
        return artistService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        artistService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merge")
    public ResponseEntity<ArtistMergeResponse> merge(@RequestBody ArtistMergeRequest request) {
        return ResponseEntity.ok(artistMergeService.merge(request));
    }

    private ArtistGenre toGenre(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ArtistGenre.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_GENRE_TYPE);
        }
    }

    private ArtistSortType toSortType(String value) {
        if (value == null || value.isBlank()) {
            return ArtistSortType.CREATED_DESC;
        }
        try {
            return ArtistSortType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new FestaException(ArtistErrorCode.ARTIST_INVALID_SORT_TYPE);
        }
    }
}
