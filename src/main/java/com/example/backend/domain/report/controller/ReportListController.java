package com.example.backend.domain.report.controller;

import com.example.backend.domain.report.repository.ReportRepository;
import com.example.backend.domain.report.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend.domain.report.dto.CollectionReportDetailResponse;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
public class ReportListController
{

    private final ReportService reportService;

    public ReportListController(ReportService reportService)
    {
        this.reportService = reportService;
    }

    @GetMapping("/api/report")
    public List<ReportRepository.ReportListItem> getReports()
    {
        return reportService.getReportList();
    }

    @GetMapping("/api/report/collection/{reportId}")
    public CollectionReportDetailResponse getCollectionReport(
            @PathVariable("reportId") Long reportId
    ) { return reportService.getCollectionReport(reportId); }
}