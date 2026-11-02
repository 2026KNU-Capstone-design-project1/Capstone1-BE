package com.example.backend.domain.report.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "reports")
public class Report
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", columnDefinition = "VARCHAR")
    private String taskName;

    @Column(name = "location", columnDefinition = "VARCHAR")
    private String location;

    @Column(name = "risk_type", columnDefinition = "VARCHAR")
    private String riskType;

    @Column(name = "risk_content", columnDefinition = "VARCHAR")
    private String riskContent;

    @Column(name = "safety_step", columnDefinition = "VARCHAR")
    private String safetyStep;

    @Column(name = "legal_basis", columnDefinition = "VARCHAR")
    private String legalBasis;

    @Column(
            name = "created_at",
            updatable = false,
            columnDefinition = "TIMESTAMPTZ"
    )
    private OffsetDateTime createdAt;

    protected Report() {}

    public Report(
            String taskName,
            String location,
            String riskType,
            String riskContent,
            String safetyStep,
            String legalBasis
    ) {
        this.taskName = taskName;
        this.location = location;
        this.riskType = riskType;
        this.riskContent = riskContent;
        this.safetyStep = safetyStep;
        this.legalBasis = legalBasis;
    }

    @PrePersist
    protected void onCreate()
    {
        if (createdAt == null)
        {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId()
    {
        return id;
    }

    public String getTaskName()
    {
        return taskName;
    }

    public String getLocation()
    {
        return location;
    }

    public String getRiskType()
    {
        return riskType;
    }

    public String getRiskContent()
    {
        return riskContent;
    }

    public String getSafetyStep()
    {
        return safetyStep;
    }

    public String getLegalBasis()
    {
        return legalBasis;
    }

    public OffsetDateTime getCreatedAt()
    {
        return createdAt;
    }
}