package com.example.backend.domain.history.dto;

import com.example.backend.domain.risk.entity.RiskStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public record HistoryResponse(
        long repeatedDetectionCount,
        long longTermUnresolvedCount,
        long redetectedAfterActionCount,
        String aiAnalysis,
        List<RiskHistoryItem> histories
) {
    public HistoryResponse() {
        this(0L, 0L, 0L, null, List.of());
    }

    public record RiskHistoryItem(
            Long riskId,
            String riskType,
            String hazard,
            String zoneName,
            String facilityName,
            LocalDateTime firstDetectedDt,
            LocalDateTime lastDetectedDt,
            long detectionCount,
            RiskStatus status,
            List<HistoryEvent> events
    ) {
    }

    public record HistoryEvent(
            Long id,
            RiskStatus status,
            OffsetDateTime changedAt,
            String eventType,
            Long cycle
    ) {
    }
}
