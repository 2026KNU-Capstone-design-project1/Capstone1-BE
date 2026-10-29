package com.example.backend.domain.risk.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name="risk_issues")
public class RiskIssue
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="facility_id")
    private Long facilityId;

    @Column(name="risk_type", length=255)
    private String riskType;

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

    @Column(
            name = "resolved_at",
            columnDefinition = "TIMESTAMPTZ"
    )
    private OffsetDateTime resolvedAt;

    protected RiskIssue() {}

    public RiskIssue(
            Long facilityId,
            String riskType,
            RiskLevel riskLevel,
            Long firstDetectedMs,
            Long lastDetectedMs
    ) {
        this.facilityId = facilityId;
        this.riskType = riskType;
        this.riskLevel = riskLevel;
        this.firstDetectedMs = firstDetectedMs;
        this.lastDetectedMs = lastDetectedMs;
        this.status = RiskStatus.OPEN;
        this.resolvedAt = null;
    }

    public void updateStatus(RiskStatus newStatus)
    {
        if (this.status == newStatus)
        {
            return;
        }

        this.status = newStatus;

        if (newStatus == RiskStatus.RESOLVED)
        {
            this.resolvedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        else
        {
            this.resolvedAt = null;
        }
    }

    public Long getId()
    {
        return id;
    }

    public Long getFacilityId()
    {
        return facilityId;
    }

    public String getRiskType()
    {
        return riskType;
    }

    public RiskStatus getStatus()
    {
        return status;
    }

    public RiskLevel getRiskLevel()
    {
        return riskLevel;
    }

    public Long getFirstDetectedMs()
    {
        return firstDetectedMs;
    }

    public Long getLastDetectedMs()
    {
        return lastDetectedMs;
    }

    public OffsetDateTime getResolvedAt()
    {
        return resolvedAt;
    }
}
