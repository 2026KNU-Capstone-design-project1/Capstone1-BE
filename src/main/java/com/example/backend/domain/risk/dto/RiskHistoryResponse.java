package com.example.backend.domain.risk.dto;

import com.example.backend.domain.risk.entity.RiskHistory;
import com.example.backend.domain.risk.entity.RiskStatus;

import java.time.OffsetDateTime;

public record RiskHistoryResponse(
        Long id,
        RiskStatus status,
        OffsetDateTime changedAt,
        String eventType,
        Long cycle
) {
    public static RiskHistoryResponse from(RiskHistory history)
    {
        return new RiskHistoryResponse(
                history.getId(),
                history.getStatus(),
                history.getChangedAt(),
                history.getEventType(),
                history.getCycle()
        );
    }
}