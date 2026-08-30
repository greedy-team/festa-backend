package com.greedy.festa.artist.controller;

import com.greedy.festa.artist.dto.ArtistDetailResponse;
import com.greedy.festa.artist.dto.ArtistListItemResponse;
import com.greedy.festa.artist.service.ArtistService;
import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.global.exception.FestaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "아티스트", description = "사용자용 아티스트 조회 API")
@RestController
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;

    @Operation(summary = "아티스트 목록 조회",
            description = "대표명·별칭 검색, 장르 필터, 출연 많은 순 또는 이름순 정렬을 지원합니다. "
                    + "출연 횟수와 최근 축제는 발행된 종료 축제만 집계합니다.")
    @ApiResponse(responseCode = "200", description = "아티스트 페이지")
    @ApiResponse(responseCode = "400", description = "INVALID_PAGE / INVALID_PAGE_SIZE / "
            + "ARTIST_INVALID_QUERY / ARTIST_INVALID_GENRE_TYPE / ARTIST_INVALID_SORT_TYPE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<ArtistListItemResponse> findAll(
            @Parameter(required = true, description = "0-based 페이지 번호")
            @RequestParam(required = false) String page,
            @Parameter(required = true, description = "페이지 크기(1~50)")
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q
    ) {
        return artistService.findAll(parsePage(page), parseSize(size), genre, sort, q);
    }

    @Operation(summary = "아티스트 상세 조회",
            description = "아티스트 기본 정보와 예정 공연·출연 이력을 각각 최대 5건 반환합니다. "
                    + "발행된 축제만 노출하며 날짜는 KST 기준입니다.")
    @ApiResponse(responseCode = "200", description = "아티스트 상세")
    @ApiResponse(responseCode = "400", description = "INVALID_PATH_VARIABLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "ARTIST_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ArtistDetailResponse findById(@PathVariable Long id) {
        return artistService.findById(id);
    }

    private int parsePage(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE);
        }
    }

    private int parseSize(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new FestaException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
    }
}
