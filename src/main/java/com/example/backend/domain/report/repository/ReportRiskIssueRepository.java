package com.example.backend.domain.report.repository;
import com.example.backend.domain.report.entity.ReportRiskIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRiskIssueRepository
        extends JpaRepository<ReportRiskIssue, Long> {

    List<ReportRiskIssue> findByReportIdOrderByIdAsc(
            Long reportId
    );

    @Query(value = """
            SELECT link.report_id
            FROM report_risk_issues link
            JOIN reports r ON r.id = link.report_id
            WHERE r.report_type = 'INDIVIDUAL'
            GROUP BY link.report_id
            HAVING COUNT(*) = 1
            """, nativeQuery = true)
    List<Long> findIndividualReportIds();

    @Query("""
            SELECT link
            FROM ReportRiskIssue link
            WHERE link.reportId IN :reportIds
            """)
    List<ReportRiskIssue> findByReportIds(
            @Param("reportIds") List<Long> reportIds
    );
}