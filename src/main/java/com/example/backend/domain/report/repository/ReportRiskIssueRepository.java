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

    @Query("""
            SELECT link.reportId
            FROM ReportRiskIssue link
            GROUP BY link.reportId
            HAVING COUNT(link.id) = 1
            """)
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