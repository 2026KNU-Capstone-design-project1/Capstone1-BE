package com.example.backend.domain.user.dto;

public record AuthResponse(
        String accessToken,
        String tokenType
) {
}