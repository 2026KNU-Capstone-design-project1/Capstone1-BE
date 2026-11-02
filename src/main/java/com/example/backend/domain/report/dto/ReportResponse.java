package com.example.backend.domain.report.dto;

import java.time.OffsetDateTime;

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
        OffsetDateTime createdAt
) {
}