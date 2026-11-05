package com.example.backend.domain.report.controller;

import com.example.backend.domain.report.repository.ReportRepository;
import com.example.backend.domain.report.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReportListController
{

    private final ReportService reportService;

    public ReportListController(ReportService reportService)
    {
        this.reportService = reportService;
    }

    @GetMapping("/api/reports")
    public List<ReportRepository.ReportListItem> getReports()
    {
        return reportService.getReportList();
    }
}