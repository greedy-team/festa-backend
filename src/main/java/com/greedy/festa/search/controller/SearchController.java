package com.greedy.festa.search.controller;

import com.greedy.festa.global.exception.ErrorResponse;
import com.greedy.festa.search.dto.SearchResponse;
import com.greedy.festa.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
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
            description = "아티스트는 대표명·별칭, 주최는 이름·약칭, 축제는 이름·주최 이름·약칭을 "
                    + "대소문자 구분 없이 부분 일치(LIKE)로 검색합니다. 입력과 저장값의 ASCII 공백(U+0020)은 무시하고, "
                    + "LIKE 메타문자(%, _, \\)는 리터럴로 검색합니다.<br><br>"
                    + "<b>Host 통용 약어</b>: 지원 목록에 명시적으로 등록된 약어를 검색어 전체로 입력하면 "
                    + "Host와 해당 Host의 공개 Festival 결과에만 정식 Host명 검색을 함께 적용합니다. "
                    + "현재 지원 목록은 연대(연세대학교), 고대(고려대학교), 홍대(홍익대학교), "
                    + "외대·한국외대(한국외국어대학교), 이대·이화여대(이화여자대학교), "
                    + "숙대·숙명여대(숙명여자대학교), 서울여대·설여대(서울여자대학교), "
                    + "동덕여대·동덕대(동덕여자대학교), 덕성여대·덕성대(덕성여자대학교), 건대(건국대학교)입니다. "
                    + "Artist 검색에는 이 확장을 적용하지 않습니다.<br><br>"
                    + "<b>한계</b>: 목록에 없는 약어·축약어는 정식 학교명으로 추론하지 않습니다. "
                    + "추천 검색어, 유사어 추론, 형태소 분석 또는 검색어 전체의 자연어 해석은 지원하지 않으므로, "
                    + "예를 들어 \"건대 축제\"를 \"건대\"와 \"축제\"로 분해해 검색하지 않습니다.<br><br>"
                    + "Host 결과는 Host ID 오름차순, Festival 결과는 개최일 내림차순 후 Festival ID 오름차순으로 정렬합니다. "
                    + "type은 ALL(기본) / ARTIST / HOST / FESTIVAL입니다.")
    @ApiResponse(responseCode = "200", description = "검색 결과")
    @ApiResponse(responseCode = "400", description = "SEARCH_INVALID_QUERY / SEARCH_INVALID_TYPE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public SearchResponse search(
            @Parameter(description = "필수 검색어입니다. 앞뒤 공백을 제거한 뒤 1~50자여야 하며, 공백만 있는 값은 400(SEARCH_INVALID_QUERY)입니다.",
                    required = true)
            @RequestParam(required = false) String q,
            @Parameter(description = "검색 대상입니다. 생략하거나 빈 값이면 ALL이며, ALL / ARTIST / HOST / FESTIVAL을 대소문자와 앞뒤 공백 없이 해석합니다.")
            @RequestParam(required = false) String type
    ) {
        return searchService.search(q, type);
    }
}
