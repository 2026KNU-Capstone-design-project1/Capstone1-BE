package com.example.backend.domain.video.dto;

import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class VideoDtos {

    private VideoDtos() {}

    public record FacilityOption(Long id, String name) {}

    public record ZoneOption(
            Long id,
            String name,
            Long siteId,
            List<FacilityOption> facilities
    ) {}

    public record UploadResponse(
            Long videoId,
            Long siteId,
            String status
    ) {}

    public record BatchItem(
            String fileName,
            boolean accepted,
            UploadResponse result,
            String message
    ) {}

    public record BatchResponse(List<BatchItem> items) {}

    public record FolderItem(
            LocalDate date,
            long videoCount,
            long analyzedCount,
            long analysisFailedCount,
            long riskIssueCount,
            long highRiskCount,
            long unresolvedCount
    ) {}

    public record VideoItem(
            Long id,
            Long siteId,
            String fileName,
            String area,
            Long zoneId,
            Long facilityId,
            String recordedAt,
            String recordedAtSource,
            OffsetDateTime uploadedAt,
            Integer durationSeconds,
            String status,
            String analysisStatus,
            int thumbnailIndex,
            String thumbnailUrl,
            String videoUrl,
            String analysisError,
            long riskIssueCount,
            long highRiskCount,
            long unresolvedRiskCount
    ) {}

    public record RetryRequest(
            @Positive Long zoneId,
            @Positive Long facilityId
    ) {}
}