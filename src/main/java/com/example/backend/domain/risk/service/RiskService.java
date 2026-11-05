package com.example.backend.domain.risk.service;

import com.example.backend.domain.risk.dto.RiskCreateRequest;
import com.example.backend.domain.risk.dto.RiskDetailResponse;
import com.example.backend.domain.risk.dto.RiskHistoryResponse;
import com.example.backend.domain.risk.dto.RiskManagerUpdateRequest;
import com.example.backend.domain.risk.dto.RiskResponse;
import com.example.backend.domain.risk.dto.RiskStatusUpdateRequest;
import com.example.backend.domain.risk.entity.RiskHistory;
import com.example.backend.domain.risk.entity.RiskIssue;
import com.example.backend.domain.risk.repository.RiskHistoryRepository;
import com.example.backend.domain.risk.repository.RiskRepository;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RiskService {

    private final RiskRepository riskRepository;
    private final RiskHistoryRepository riskHistoryRepository;

    public RiskService(
            RiskRepository riskRepository,
            RiskHistoryRepository riskHistoryRepository
    ) {
        this.riskRepository = riskRepository;
        this.riskHistoryRepository = riskHistoryRepository;
    }

    @Transactional
    public RiskResponse create(RiskCreateRequest request) {
        if (request.lastDetectedMs() < request.firstDetectedMs()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "마지막 감지 시점은 첫 감지 시점보다 빠를 수 없습니다."
            );
        }

        RiskIssue risk = new RiskIssue(
                request.facilityId(),
                request.riskType().trim(),
                request.hazard(),
                request.riskLevel(),
                request.firstDetectedMs(),
                request.lastDetectedMs(),
                request.evidence(),
                request.location()
        );

        RiskIssue savedRisk = riskRepository.save(risk);

        riskHistoryRepository.save(new RiskHistory(
                savedRisk.getId(),
                savedRisk.getStatus(),
                savedRisk.getCreatedAt(),
                "DETECTED",
                1L
        ));

        return toResponse(savedRisk);
    }

    public List<RiskResponse> getRisks(Long facilityId) {
        if (facilityId != null && facilityId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "시설물 ID는 양수여야 합니다."
            );
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "id");

        List<RiskIssue> risks = facilityId == null
                ? riskRepository.findAll(sort)
                : riskRepository.findByFacilityId(facilityId, sort);

        return risks.stream()
                .map(this::toResponse)
                .toList();
    }

    public RiskDetailResponse getRisk(Long id) {
        RiskIssue risk = findRisk(id);

        String facilityName = risk.getFacilityId() == null
                ? null
                : riskRepository.findFacilityName(risk.getFacilityId());

        RiskRepository.FacilityContext context = risk.getFacilityId() == null
                ? null
                : riskRepository.findFacilityContext(risk.getFacilityId());

        List<RiskHistoryResponse> history = riskHistoryRepository
                .findByRiskIssueIdOrderByChangedAtAscIdAsc(id)
                .stream()
                .map(RiskHistoryResponse::from)
                .toList();

        List<RiskDetailResponse.RelatedVideoResponse> relatedVideos =
                riskRepository.findRelatedVideos(id).stream()
                        .map(video -> new RiskDetailResponse.RelatedVideoResponse(
                                video.getVideoId(),
                                video.getVideoUrl()))
                        .toList();

        return RiskDetailResponse.from(
                toResponse(risk),
                facilityName,
                history,
                context == null ? null : context.getSiteName(),
                context == null ? null : context.getZoneName(),
                risk.getImageUrl(),
                risk.getBbox(),
                relatedVideos
        );
    }

    @Transactional
    public RiskResponse updateStatus(
            Long id,
            RiskStatusUpdateRequest request
    ) {
        RiskIssue risk = findRisk(id);

        if (risk.getStatus() == request.status())
        {
            return toResponse(risk);
        }

        risk.updateStatus(request.status());
        OffsetDateTime changedAt = risk.getResolvedAt() != null
                ? risk.getResolvedAt()
                : OffsetDateTime.now(ZoneOffset.UTC);

        riskHistoryRepository.save(new RiskHistory(
                risk.getId(),
                risk.getStatus(),
                changedAt,
                "STATUS_CHANGED",
                riskHistoryRepository.findCurrentCycle(risk.getId())
        ));
        return toResponse(risk);
    }

    @Transactional
    public RiskResponse updateManager(
            Long id,
            RiskManagerUpdateRequest request
    ) {
        RiskIssue risk = findRisk(id);
        if (request.userId() != null && !riskRepository.existManager(request.userId()))
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SITE_MANAGER 역할의 사용자를 선택해주세요.");
        }

        risk.updateManager(request.userId());
        return toResponse(risk);
    }

    private RiskResponse toResponse(RiskIssue risk)
    {
        String managerName = risk.getUserId() == null ? null : riskRepository.findManagerName(risk.getUserId());
        return RiskResponse.from(risk, managerName);
    }

    private RiskIssue findRisk(Long id) {
        return riskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "위험요소를 찾을 수 없습니다."
                ));
    }
}