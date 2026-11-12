package com.example.backend.domain.history.dto;

import java.util.List;

public record HistoryAiAnalysisInput(
        int longTermThresholdDays,
        List<RepeatedRisk> repeatedRisks,
        List<LongUnresolvedRisk> longUnresolvedRisks,
        List<ZoneSummary> zoneSummaries
) {
    public record RepeatedRisk(
            String riskType,
            String hazard,
            String zoneName,
            long cycleCount
    ) {
    }

    public record LongUnresolvedRisk(
            String riskType,
            String hazard,
            String zoneName,
            long unresolvedDays
    ) {
    }

    public record ZoneSummary(
            String zoneName,
            long repeatedRiskCount,
            long longUnresolvedRiskCount
    ) {
    }
}
