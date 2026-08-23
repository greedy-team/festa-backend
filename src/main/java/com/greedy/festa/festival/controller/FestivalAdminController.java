package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.dto.FestivalPublishResponse;
import com.greedy.festa.festival.dto.FestivalReviewItem;
import com.greedy.festa.festival.dto.FestivalSortType;
import com.greedy.festa.festival.service.FestivalAdminService;
import com.greedy.festa.festival.service.FestivalCoverageService;
import com.greedy.festa.global.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/festivals")
@RequiredArgsConstructor
public class FestivalAdminController {

    private final FestivalAdminService festivalAdminService;
    private final FestivalCoverageService festivalCoverageService;

    @GetMapping
    public PageResponse<FestivalReviewItem> findAll(
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String discovery,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return festivalAdminService.findAll(
                published, hostId, year, q, discovery, FestivalSortType.from(sort), page, size
        );
    }

    @GetMapping("/coverage")
    public FestivalCoverageResponse findCoverage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return festivalCoverageService.findCoverage(year, status, page, size);
    }

    @PostMapping("/{id}/publish")
    public FestivalPublishResponse publish(@PathVariable Long id) {
        return festivalAdminService.publish(id);
    }

    @DeleteMapping("/{id}/publish")
    public FestivalPublishResponse unpublish(@PathVariable Long id) {
        return festivalAdminService.unpublish(id);
    }
}
