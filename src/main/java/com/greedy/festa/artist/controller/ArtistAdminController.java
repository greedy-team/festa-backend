package com.greedy.festa.artist.controller;

import com.greedy.festa.artist.dto.ArtistCreateRequest;
import com.greedy.festa.artist.dto.ArtistMergeCandidateResponse;
import com.greedy.festa.artist.dto.ArtistMergeRequest;
import com.greedy.festa.artist.dto.ArtistMergeResponse;
import com.greedy.festa.artist.dto.ArtistResponse;
import com.greedy.festa.artist.dto.ArtistSortType;
import com.greedy.festa.artist.dto.ArtistUpdateRequest;
import com.greedy.festa.artist.entity.ArtistGenre;
import com.greedy.festa.artist.service.ArtistAdminService;
import com.greedy.festa.artist.service.ArtistMergeCandidateService;
import com.greedy.festa.artist.service.ArtistMergeService;
import com.greedy.festa.global.config.SwaggerConfig;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "관리자 - 아티스트", description = "아티스트의 등록·목록·수정·삭제와 중복 아티스트 병합. "
        + "토큰이 없거나 만료되면 401(UNAUTHORIZED / TOKEN_EXPIRED)이다.")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/artists")
@RequiredArgsConstructor
public class ArtistAdminController {

    private final ArtistAdminService artistAdminService;
    private final ArtistMergeService artistMergeService;
    private final ArtistMergeCandidateService artistMergeCandidateService;

    @Operation(summary = "아티스트 등록",
            description = "이름은 기존 아티스트의 이름뿐 아니라 별칭과도 겹칠 수 없다.")
    @ApiResponse(responseCode = "201", description = "등록된 아티스트")
    @ApiResponse(responseCode = "400", description = "ARTIST_INVALID_NAME / ARTIST_INVALID_ALIAS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "ARTIST_DUPLICATE_NAME / ARTIST_DUPLICATE_ALIAS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<ArtistResponse> create(@RequestBody ArtistCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(artistAdminService.create(request));
    }

    @Operation(summary = "아티스트 목록 조회",
            description = "q는 이름과 별칭을 함께 찾는다. "
                    + "genre는 HIPHOP / BALLAD_RNB / DANCE / BAND, sort는 CREATED_DESC(기본) / APPEARANCES / NAME.")
    @ApiResponse(responseCode = "200", description = "아티스트 페이지")
    @ApiResponse(responseCode = "400", description = "INVALID_PAGE / INVALID_PAGE_SIZE / ARTIST_INVALID_QUERY "
            + "/ ARTIST_INVALID_GENRE_TYPE / ARTIST_INVALID_SORT_TYPE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<ArtistResponse> findAll(
            @RequestParam(required = false) Boolean needsReview,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return artistAdminService.findAll(needsReview, q,
                ArtistGenre.from(genre),
                ArtistSortType.from(sort),
                page, size);
    }

    @Operation(summary = "병합 후보 조회",
            description = "이름이나 별칭이 겹치는 아티스트만 후보가 되고, 같은 축제 출연은 점수를 올릴 뿐 단독으로는 후보가 되지 않는다. "
                    + "similarity 내림차순, 출연 횟수 내림차순, artistId 오름차순으로 정렬해 limit(1~20)만큼 준다.")
    @ApiResponse(responseCode = "200", description = "기준 아티스트와 병합 후보 목록")
    @ApiResponse(responseCode = "400", description = "ARTIST_INVALID_LIMIT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}/merge-candidates")
    public ArtistMergeCandidateResponse findMergeCandidates(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") Long limit) {
        return artistMergeCandidateService.findAll(id, limit);
    }

    @Operation(summary = "아티스트 수정",
            description = "보내온 항목만 반영한다. otherNames를 보내면 그 목록으로 통째로 교체된다.")
    @ApiResponse(responseCode = "200", description = "수정된 아티스트")
    @ApiResponse(responseCode = "400", description = "ARTIST_INVALID_NAME / ARTIST_INVALID_ALIAS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "ARTIST_DUPLICATE_NAME / ARTIST_DUPLICATE_ALIAS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}")
    public ArtistResponse update(@PathVariable Long id, @RequestBody ArtistUpdateRequest request) {
        return artistAdminService.update(id, request);
    }

    @Operation(summary = "아티스트 삭제",
            description = "출연 이력이 하나라도 있으면 지우지 않는다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @ApiResponse(responseCode = "404", description = "ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "ARTIST_HAS_APPEARANCES - 출연 이력이 있는 아티스트",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        artistAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "아티스트 병합",
            description = "sourceIds를 targetId 하나로 합치고 source는 삭제한다. "
                    + "keepAliases가 true(기본)면 흡수된 이름이 target의 별칭으로 남는다.")
    @ApiResponse(responseCode = "200", description = "병합 결과")
    @ApiResponse(responseCode = "400", description = "ARTIST_INVALID_TARGET_ID / ARTIST_INVALID_SOURCE_IDS "
            + "/ ARTIST_SELF_MERGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/merge")
    public ResponseEntity<ArtistMergeResponse> merge(@RequestBody ArtistMergeRequest request) {
        return ResponseEntity.ok(artistMergeService.merge(request));
    }
}
