package com.example.backend.domain.report.dto;

import java.time.OffsetDateTime;
import com.example.backend.domain.risk.dto.RiskDetailResponse.BboxResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ReportResponse(
        Long reportId,
        Long riskId,
        String taskName,
        String location,
        String riskType,
        String riskContent,
        String safetyStep,
        String legalBasis,
        String riskLevel,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        String imageUrl,
        BboxResponse bbox
) { }