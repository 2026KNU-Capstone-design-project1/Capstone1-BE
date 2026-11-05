package com.example.backend.domain.risk.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "risks_issue")
public class RiskIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id")
    private Long facilityId;

    @Column(name = "risk_type", length = 255)
    private String riskType;

    @Column(name = "hazard", length = 255)
    private String hazard;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 255)
    private RiskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 255)
    private RiskLevel riskLevel;

    @Column(name = "first_detected_ms")
    private Long firstDetectedMs;

    @Column(name = "last_detected_ms")
    private Long lastDetectedMs;

    @Column(name = "location", length = 1000)
    private String location;

    @Column(
            name = "resolved_at",
            columnDefinition = "TIMESTAMPTZ"
    )
    private OffsetDateTime resolvedAt;

    @Column(name = "evidence", length = 255)
    private String evidence;

    @Column(
            name = "created_at",
            updatable = false,
            columnDefinition = "TIMESTAMPTZ"
    )
    private OffsetDateTime createdAt;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "image_url")
    private String imageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bbox", columnDefinition = "jsonb")
    private Map<String, Double> bbox;

    protected RiskIssue() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public RiskIssue(
            Long facilityId,
            String riskType,
            String hazard,
            RiskLevel riskLevel,
            Long firstDetectedMs,
            Long lastDetectedMs,
            String evidence,
            String location
            ) {
        this.facilityId = facilityId;
        this.riskType = riskType;
        this.hazard = hazard;
        this.riskLevel = riskLevel;
        this.firstDetectedMs = firstDetectedMs;
        this.lastDetectedMs = lastDetectedMs;
        this.evidence = evidence;
        this.status = RiskStatus.OPEN;
        this.resolvedAt = null;
        this.location = location;
    }

    public void updateStatus(RiskStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }

        this.status = newStatus;

        if (newStatus == RiskStatus.RESOLVED) {
            this.resolvedAt = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            this.resolvedAt = null;
        }
    }

    public void updateManager(Long userId) {
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getFacilityId() {
        return facilityId;
    }

    public String getRiskType() {
        return riskType;
    }

    public String getHazard() {
        return hazard;
    }

    public RiskStatus getStatus() {
        return status;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Long getFirstDetectedMs() {
        return firstDetectedMs;
    }

    public Long getLastDetectedMs() {
        return lastDetectedMs;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public String getEvidence() {
        return evidence;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLocation() {
        return location;
    }

    public String getImageUrl() { return imageUrl; }

    public Map<String, Double> getBbox() { return bbox; }
}