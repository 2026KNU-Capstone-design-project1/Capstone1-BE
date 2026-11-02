package com.example.backend.domain.report.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "report_risk_issues")
public class ReportRiskIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "risk_issue_id", nullable = false)
    private Long riskIssueId;

    protected ReportRiskIssue() {}

    public ReportRiskIssue(
            Long reportId,
            Long riskIssueId
    ) {
        this.reportId = reportId;
        this.riskIssueId = riskIssueId;
    }

    public Long getId()
    {
        return id;
    }

    public Long getReportId()
    {
        return reportId;
    }

    public Long getRiskIssueId()
    {
        return riskIssueId;
    }
}