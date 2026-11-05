package com.example.backend.domain.risk.dto;
import com.example.backend.domain.risk.entity.RiskLevel;
import com.example.backend.domain.risk.entity.RiskStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record RiskDetailResponse(
        Long id,
        Long facilityId,
        String facilityName,
        String riskType,
        String hazard,
        RiskStatus status,
        RiskLevel riskLevel,
        Long firstDetectedMs,
        Long lastDetectedMs,
        OffsetDateTime resolvedAt,
        String evidence,
        OffsetDateTime createdAt,
        Long userId,
        String managerName,
        String location,
        List<RiskHistoryResponse> history,
        String siteName,
        String zoneName,
        String imageUrl,
        BboxResponse bbox,
        List<RelatedVideoResponse> relatedVideos
) {
    public record BboxResponse(Double x, Double y, Double width, Double height) {
        private static BboxResponse from(Map<String, Double> bbox) {
            if (bbox == null) {
                return null;
            }

            Double width = bbox.get("width");
            if (width == null) {
                width = bbox.get("wide");
            }

            return new BboxResponse(
                    bbox.get("x"),
                    bbox.get("y"),
                    width,
                    bbox.get("height")
            );
        }
    }

    public record RelatedVideoResponse(Long videoId, String videoUrl) {}
    public static RiskDetailResponse from(
            RiskResponse risk,
            String facilityName,
            List<RiskHistoryResponse> history,
            String siteName,
            String zoneName,
            String imageUrl,
            Map<String, Double> bbox,
            List<RelatedVideoResponse> relatedVideos
    ) {
        return new RiskDetailResponse(
                risk.id(),
                risk.facilityId(),
                facilityName,
                risk.riskType(),
                risk.hazard(),
                risk.status(),
                risk.riskLevel(),
                risk.firstDetectedMs(),
                risk.lastDetectedMs(),
                risk.resolvedAt(),
                risk.evidence(),
                risk.createdAt(),
                risk.userId(),
                risk.managerName(),
                risk.location(),
                history,
                siteName,
                zoneName,
                imageUrl,
                BboxResponse.from(bbox),
                relatedVideos
        );
    }
}
