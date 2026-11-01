package com.example.backend.domain.risk.service;

import com.example.backend.domain.risk.dto.RiskCreateRequest;
import com.example.backend.domain.risk.dto.RiskResponse;
import com.example.backend.domain.risk.dto.RiskStatusUpdateRequest;
import com.example.backend.domain.risk.entity.RiskIssue;
import com.example.backend.domain.risk.repository.RiskRepository;
import com.example.backend.domain.risk.dto.RiskManagerUpdateRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class RiskService {

    private final RiskRepository riskRepository;

    public RiskService(RiskRepository riskRepository) {
        this.riskRepository = riskRepository;
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
                request.evidence()
        );

        RiskIssue savedRisk = riskRepository.save(risk);

        return toResponse(savedRisk);
    }

    public Page<RiskResponse> getRisks(
            Long facilityId,
            int page,
            int size
    ) {
        if (facilityId != null && facilityId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "시설물 ID는 양수여야 합니다."
            );
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page는 0 이상, size는 1~100이어야 합니다."
            );
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        Page<RiskIssue> risks;

        if (facilityId == null) {
            risks = riskRepository.findAll(pageable);
        } else {
            risks = riskRepository.findByFacilityId(
                    facilityId,
                    pageable
            );
        }

        return risks.map(this::toResponse);
    }

    public RiskResponse getRisk(Long id) {
        return toResponse(findRisk(id));
    }

    @Transactional
    public RiskResponse updateStatus(
            Long id,
            RiskStatusUpdateRequest request
    ) {
        RiskIssue risk = findRisk(id);

        risk.updateStatus(request.status());

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