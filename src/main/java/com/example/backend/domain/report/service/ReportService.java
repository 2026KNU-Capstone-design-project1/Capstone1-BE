package com.example.backend.domain.report.service;

import com.example.backend.domain.report.dto.AiReportRequest;
import com.example.backend.domain.report.dto.ReportResponse;
import com.example.backend.domain.report.entity.Report;
import com.example.backend.domain.report.entity.ReportRiskIssue;
import com.example.backend.domain.report.repository.ReportRepository;
import com.example.backend.domain.report.repository.ReportRiskIssueRepository;
import com.example.backend.domain.risk.dto.RiskDetailResponse.BboxResponse;

import com.example.backend.domain.risk.entity.RiskIssue;
import com.example.backend.domain.risk.repository.RiskRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportRiskIssueRepository linkRepository;
    private final RiskRepository riskRepository;
    private final ObjectMapper objectMapper;

    public ReportService(
            ReportRepository reportRepository,
            ReportRiskIssueRepository linkRepository,
            RiskRepository riskRepository,
            ObjectMapper objectMapper
    ) {
        this.reportRepository = reportRepository;
        this.linkRepository = linkRepository;
        this.riskRepository = riskRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReportResponse createIndividual(
            Long riskId,
            String taskName,
            AiReportRequest request
    ) {
        if (riskId == null || riskId <= 0)
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 riskId를 입력해주세요."
            );
        }

        if (request.reportId() != null)
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신규 보고서의 report_id는 null로 보내주세요."
            );
        }

        if (taskName != null && taskName.length() > 255)
        {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "작업명은 255자 이하여야 합니다."
            );
        }

        RiskIssue risk = findRisk(riskId);

        Report report = new Report(
                normalize(taskName),
                request.sceneInfo().location().trim(),
                toText(request.riskAssessment().expectedAccidents()),
                toText(request.improvementMeasures()),
                toText(request.safetyMeasures()),
                toText(request.legalInformation())
        );

        Report saved = reportRepository.save(report);

        linkRepository.save(new ReportRiskIssue(
                saved.getId(),
                riskId
        ));

        return toResponse(saved, risk);
    }

    public List<ReportResponse> getIndividualReports()
    {
        List<Long> reportIds =
                linkRepository.findIndividualReportIds();

        if (reportIds.isEmpty())
        {
            return List.of();
        }

        List<Report> reports =
                reportRepository.findAllById(reportIds);

        reports.sort(
                Comparator.comparing(
                        Report::getCreatedAt,
                        Comparator.nullsLast(
                                Comparator.reverseOrder()
                        )
                ).thenComparing(
                        Report::getId,
                        Comparator.reverseOrder()
                )
        );

        Map<Long, Long> riskIdByReportId = linkRepository
                .findByReportIds(reportIds)
                .stream()
                .collect(Collectors.toMap(
                        ReportRiskIssue::getReportId,
                        ReportRiskIssue::getRiskIssueId
                ));

        List<Long> riskIds = riskIdByReportId.values()
                .stream()
                .distinct()
                .toList();

        Map<Long, RiskIssue> riskById = riskRepository
                .findAllById(riskIds)
                .stream()
                .collect(Collectors.toMap(
                        RiskIssue::getId,
                        Function.identity()
                ));

        return reports.stream()
                .map(report -> {
                    Long riskId = riskIdByReportId.get(
                            report.getId()
                    );

                    RiskIssue risk = riskById.get(riskId);

                    if (risk == null) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "보고서에 연결된 위험요소가 없습니다."
                        );
                    }

                    return toResponse(report, risk);
                })
                .toList();
    }

    public List<ReportRepository.ReportListItem> getReportList()
    {
        return reportRepository.findReportList();
    }

    public ReportResponse getIndividualReport(Long reportId)
    {
        Report report = reportRepository.findIndividualById(reportId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "보고서를 찾을 수 없습니다."
                ));

        List<ReportRiskIssue> links = linkRepository
                .findByReportIdOrderByIdAsc(reportId);

        if (links.size() != 1)
        {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "개별 보고서가 아니거나 연결 정보가 없습니다."
            );
        }

        RiskIssue risk = findRisk(
                links.getFirst().getRiskIssueId()
        );
        return toResponse(report, risk);
    }

    private RiskIssue findRisk(Long riskId)
    {
        return riskRepository.findById(riskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "위험요소를 찾을 수 없습니다."
                ));
    }

    private ReportResponse toResponse(
            Report report,
            RiskIssue risk
    ) {
        return new ReportResponse(
                report.getId(),
                risk.getId(),
                report.getTaskName(),
                report.getLocation(),
                report.getRiskType(),
                report.getRiskContent(),
                report.getSafetyStep(),
                report.getLegalBasis(),
                risk.getRiskLevel() == null ? null : risk.getRiskLevel().name(),
                report.getCreatedAt(),
                risk.getImageUrl(),
                BboxResponse.from(risk.getBbox())
        );
    }

    private String toText(List<Object> values)
    {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream()
                .map(value -> {
                    if (value instanceof String text)
                    {
                        return text;
                    }

                    return objectMapper.writeValueAsString(value);
                })
                .collect(Collectors.joining("\n"));
    }
    private String normalize(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}