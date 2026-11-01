package com.example.backend.domain.risk.dto;

import jakarta.validation.constraints.Positive;

public record RiskManagerUpdateRequest(

        @Positive
        Long userId
) {}