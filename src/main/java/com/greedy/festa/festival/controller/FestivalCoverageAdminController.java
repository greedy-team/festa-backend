package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.service.FestivalCoverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/festivals")
@RequiredArgsConstructor
public class FestivalCoverageAdminController {

    private final FestivalCoverageService festivalCoverageService;

    @GetMapping("/coverage")
    public FestivalCoverageResponse findCoverage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status,
            Pageable pageable
    ) {
        return festivalCoverageService.findCoverage(year, status, pageable);
    }
}
