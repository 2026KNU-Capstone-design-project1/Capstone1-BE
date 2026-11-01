package com.example.backend.domain.risk.dto;

import com.example.backend.domain.risk.entity.RiskIssue;
import com.example.backend.domain.risk.entity.RiskLevel;
import com.example.backend.domain.risk.entity.RiskStatus;

import java.time.OffsetDateTime;

public record RiskResponse(
        Long id,
        Long facilityId,
        String riskType,
        String hazard,
        RiskStatus status,
        RiskLevel riskLevel,
        Long firstDetectedMs,
        Long lastDetectedMs,
        OffsetDateTime resolvedAt,
<<<<<<< HEAD
        String evidence,
        OffsetDateTime createdAt,
        Long userId,
        String managerName
) {

    public static RiskResponse from(RiskIssue risk, String managerName)
=======
        String evidence
) {

    public static RiskResponse from(RiskIssue risk)
>>>>>>> origin/dev
    {
        return new RiskResponse(
                risk.getId(),
                risk.getFacilityId(),
                risk.getRiskType(),
                risk.getHazard(),
                risk.getStatus(),
                risk.getRiskLevel(),
                risk.getFirstDetectedMs(),
                risk.getLastDetectedMs(),
                risk.getResolvedAt(),
<<<<<<< HEAD
                risk.getEvidence(),
                risk.getCreatedAt(),
                risk.getUserId(),
                managerName
=======
                risk.getEvidence()
>>>>>>> origin/dev
        );
    }
}