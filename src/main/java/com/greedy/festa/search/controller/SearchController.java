package com.greedy.festa.search.controller;

import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.search.dto.SearchResponse;
import com.greedy.festa.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "검색", description = "서비스 사용자용 통합 검색 API")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "통합 검색",
            description = "아티스트 대표명·별칭, 주최 이름·약칭, 축제 이름·주최 이름을 검색합니다. "
                    + "type은 ALL(기본) / ARTIST / HOST / FESTIVAL입니다.")
    @ApiResponse(responseCode = "200", description = "검색 결과")
    @ApiResponse(responseCode = "400", description = "SEARCH_INVALID_QUERY / SEARCH_INVALID_TYPE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type
    ) {
        return searchService.search(q, type);
    }
}
