package com.example.backend.domain.report.controller;
import com.example.backend.domain.report.dto.AiReportRequest;
import com.example.backend.domain.report.dto.ReportResponse;
import com.example.backend.domain.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/reports/individual")
public class ReportController
{

    private final ReportService reportService;

    public ReportController(ReportService reportService)
    {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<ReportResponse> create(
            @RequestParam("riskId") Long riskId,

            @RequestParam(
                    name = "taskName",
                    required = false
            )
            String taskName,

            @Valid @RequestBody AiReportRequest request
    ) {
        ReportResponse response =
                reportService.createIndividual(
                        riskId,
                        taskName,
                        request
                );

        return ResponseEntity
                .created(URI.create(
                        "/api/reports/individual/"
                                + response.reportId()
                ))
                .body(response);
    }

    @GetMapping
    public List<ReportResponse> getReports()
    {
        return reportService.getIndividualReports();
    }

    @GetMapping("/{reportId}")
    public ReportResponse getReport(
            @PathVariable("reportId") Long reportId
    ) {
        return reportService.getIndividualReport(reportId);
    }
}