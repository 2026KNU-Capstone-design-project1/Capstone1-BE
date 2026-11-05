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

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime changedAt;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "cycle")
    private Long cycle;

    protected RiskHistory() {}

    public RiskHistory(
            Long riskIssueId,
            RiskStatus status,
            OffsetDateTime changedAt,
            String eventType,
            Long cycle
    ) {
        this.riskIssueId = riskIssueId;
        this.status = status;
        this.changedAt = changedAt;
        this.eventType = eventType;
        this.cycle = cycle;
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

    public String getEventType()
    {
        return eventType;
    }

    public Long getCycle()
    {
        return cycle;
    }
}