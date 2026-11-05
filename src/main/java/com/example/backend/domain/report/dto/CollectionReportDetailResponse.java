package com.example.backend.domain.report.dto;

import tools.jackson.databind.JsonNode;

public record CollectionReportDetailResponse(
        Long reportId,
        String reportType,
        String title,
        String analysisPeriod,
        Long includedRiskCount,
        JsonNode content
) {}