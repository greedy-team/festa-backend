package com.greedy.festa.festival.controller;

import com.greedy.festa.festival.dto.FestivalCoverageResponse;
import com.greedy.festa.festival.service.FestivalCoverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/festivals")
@RequiredArgsConstructor
public class FestivalAdminController {

    private final FestivalCoverageService festivalCoverageService;

    @GetMapping("/coverage")
    public FestivalCoverageResponse findCoverage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return festivalCoverageService.findCoverage(year, status, page, size);
    }
}
