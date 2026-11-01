package com.example.backend.domain.risk.dto;

import com.example.backend.domain.risk.entity.RiskIssue;
import com.example.backend.domain.risk.entity.RiskLevel;
import com.example.backend.domain.risk.entity.RiskStatus;

import java.time.OffsetDateTime;

public record RiskResponse(
        Long id,
        Long facilityId,
        String riskType,
        RiskStatus status,
        RiskLevel riskLevel,
        Long firstDetectedMs,
        Long lastDetectedMs,
        OffsetDateTime resolvedAt
) {
    public static RiskResponse from(RiskIssue risk)
    {
        return new RiskResponse(
                risk.getId(),
                risk.getFacilityId(),
                risk.getRiskType(),
                risk.getStatus(),
                risk.getRiskLevel(),
                risk.getFirstDetectedMs(),
                risk.getLastDetectedMs(),
                risk.getResolvedAt()
        );
    }
}