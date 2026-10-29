package com.example.backend.domain.risk.dto;

import com.example.backend.domain.risk.entity.RiskStatus;

import jakarta.validation.constraints.NotNull;

public record RiskStatusUpdateRequest(

        @NotNull
        RiskStatus status
) {}