package com.example.backend.domain.risk.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "risk_history")
public class RiskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "risk_issue_id", nullable = false)
    private Long riskIssueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 255, nullable = false)
    private RiskStatus status;

    @Column(
            name = "changed_at",
            nullable = false,
            columnDefinition = "TIMESTAMPTZ"
    )
    private OffsetDateTime changedAt;

    protected RiskHistory() {}

    public RiskHistory(
            Long riskIssueId,
            RiskStatus status,
            OffsetDateTime changedAt
    ) {
        this.riskIssueId = riskIssueId;
        this.status = status;
        this.changedAt = changedAt;
    }

    public Long getId()
    {
        return id;
    }

    public Long getRiskIssueId()
    {
        return riskIssueId;
    }

    public RiskStatus getStatus()
    {
        return status;
    }

    public OffsetDateTime getChangedAt()
    {
        return changedAt;
    }
}