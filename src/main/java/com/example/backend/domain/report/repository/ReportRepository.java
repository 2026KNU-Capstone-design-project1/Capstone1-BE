package com.example.backend.domain.report.repository;
import com.example.backend.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long>
    {
        interface ReportListItem {
            Long getReportId();
            String getReportType();
            String getTitle();
            String getHazard();
            String getLocation();
            String getRiskType();
            String getRiskLevel();
            String getAnalysisPeriod();
            Long getIncludedRiskCount();
    }

    @Query(value = """
            SELECT r.id AS "reportId",
                   r.report_type AS "reportType",
                   CASE WHEN r.report_type = 'COLLECTION' THEN r.title END AS "title",
                   CASE WHEN r.report_type = 'INDIVIDUAL'THEN linked.hazard END AS "hazard",
                   CASE WHEN r.report_type = 'INDIVIDUAL' THEN linked.location END AS "location",
                   CASE WHEN r.report_type = 'INDIVIDUAL' THEN linked.risk_type END AS "riskType",
                   CASE WHEN r.report_type = 'INDIVIDUAL' THEN linked.risk_level END AS "riskLevel",
                   CASE WHEN r.report_type = 'COLLECTION'THEN to_char(month.start_at - interval '1 month', 'YYYY.MM.DD')
                             || '~' ||
                             to_char(month.start_at - interval '1 day', 'YYYY.MM.DD')
                        END AS "analysisPeriod",
                   CASE WHEN r.report_type = 'COLLECTION' THEN linked.risk_count END AS "includedRiskCount"
            FROM reports r CROSS JOIN LATERAL (
                SELECT date_trunc('month', r.created_at AT TIME ZONE 'Asia/Seoul') AS start_at
            ) month
            LEFT JOIN LATERAL (
                 SELECT count(DISTINCT link.risk_issue_id) AS risk_count,
                        max(risk.hazard) AS hazard,
                        max(risk.location) AS location,
                        max(risk.risk_type) AS risk_type,
                        max(risk.risk_level) AS risk_level
                 FROM report_risk_issues link
                 LEFT JOIN risks_issue risk ON risk.id = link.risk_issue_id
                 WHERE link.report_id = r.id
            ) linked ON TRUE
            ORDER BY r.created_at DESC NULLS LAST, r.id DESC
            """, nativeQuery = true)
    List<ReportListItem> findReportList();
        @Query(value = """
        SELECT * FROM reports WHERE id = :reportId AND report_type = 'INDIVIDUAL'
            """, nativeQuery = true)
        java.util.Optional<Report> findIndividualById(
                @org.springframework.data.repository.query.Param("reportId")
                Long reportId
        );
}