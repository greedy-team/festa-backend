package com.greedy.festa.admin.controller;

import com.greedy.festa.admin.dto.AdminLoginRequest;
import com.greedy.festa.admin.dto.AdminLoginResponse;
import com.greedy.festa.admin.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public AdminLoginResponse login(@RequestBody AdminLoginRequest request) {
        return adminAuthService.login(request);
    }

}
