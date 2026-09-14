package com.greedy.festa.admin.dto;

import java.time.Duration;

public record AdminLoginResponse(
        String accessToken, long expiresIn
) {
    public static AdminLoginResponse of(String accessToken, Duration validity) {
        return new AdminLoginResponse(accessToken, validity.toSeconds());
    }
}
