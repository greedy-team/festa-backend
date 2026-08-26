package com.greedy.festa.admin.controller;

import com.greedy.festa.admin.dto.AdminLoginRequest;
import com.greedy.festa.admin.dto.AdminLoginResponse;
import com.greedy.festa.admin.service.AdminAuthService;
import com.greedy.festa.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 인증", description = "관리자 토큰 발급. 여기서 받은 토큰을 다른 관리자 API의 Authorization 헤더에 쓴다.")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "관리자 로그인", description = "아이디와 비밀번호로 관리자 토큰을 발급받는다.")
    @ApiResponse(responseCode = "200", description = "토큰 발급")
    @ApiResponse(responseCode = "401", description = "ADMIN_INVALID_CREDENTIALS - 아이디 또는 비밀번호가 올바르지 않다",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public AdminLoginResponse login(@RequestBody AdminLoginRequest request) {
        return adminAuthService.login(request);
    }

}
