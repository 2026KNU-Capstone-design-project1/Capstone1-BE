package com.example.backend.domain.risk.dto;

import com.example.backend.domain.risk.entity.RiskLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RiskCreateRequest(

        @NotNull
        @Positive
        Long facilityId,

        @NotBlank
        @Size(max = 255)
        String riskType,

        @NotNull
        RiskLevel riskLevel,

        @NotNull
        @PositiveOrZero
        Long firstDetectedMs,

        @NotNull
        @PositiveOrZero
        Long lastDetectedMs
) {}