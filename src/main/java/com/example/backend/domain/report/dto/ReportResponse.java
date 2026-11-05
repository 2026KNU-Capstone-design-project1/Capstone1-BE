package com.example.backend.domain.report.dto;

import java.time.OffsetDateTime;
import com.example.backend.domain.risk.dto.RiskDetailResponse.BboxResponse;

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
        OffsetDateTime createdAt,
        String imageUrl,
        BboxResponse bbox
) { }