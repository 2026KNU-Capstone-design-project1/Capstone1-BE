package com.example.backend.domain.dashboard.dto;

import java.util.List;

public record DashboardResponse(
        SiteItem site,
        List<SiteItem> sites,
        UserItem user,
        List<MetricItem> metrics,
        RiskItem featuredRisk,
        List<RiskItem> unresolvedRisks,
        List<MapRiskItem> riskZones
) {
    public record SiteItem(
            Long id,
            String name
    ) {}

    public record UserItem(
            String name,
            String role
    ) {}

    public record MetricItem(
            String id,
            String label,
            Number value,
            String suffix,
            String icon,
            String tone
    ) {}

    public record RiskItem(
            Long id,
            String image,
            String area,
            String facility,
            String content,
            String riskType,
            String severity,
            String detectedAt,
            String status,
            String statusDate,
            Long userId,
            String assignee
    ) {}

    public record MapRiskItem(
            Long id,
            String hazard,
            String severity,
            String status,
            Long facilityId,
            String facilityName,
            String engName,
            Long zoneId,
            String detectedAt
    ) {}
}