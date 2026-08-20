package com.greedy.festa.host.controller;

import com.greedy.festa.global.dto.PageResponse;
import com.greedy.festa.host.dto.HostCreateRequest;
import com.greedy.festa.host.dto.HostResponse;
import com.greedy.festa.host.dto.HostUpdateRequest;
import com.greedy.festa.host.service.HostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/hosts")
@RequiredArgsConstructor
public class HostAdminController {

    private final HostService hostService;

    @PostMapping
    public ResponseEntity<HostResponse> create(@RequestBody HostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hostService.create(request));
    }

    @GetMapping
    public PageResponse<HostResponse> findAll(Pageable pageable) {
        return hostService.findAll(pageable);
    }

    @PatchMapping("/{id}")
    public HostResponse update(@PathVariable Long id, @RequestBody HostUpdateRequest request) {
        return hostService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        hostService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
