package com.example.backend.domain.risk.repository;

import com.example.backend.domain.risk.entity.RiskHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskHistoryRepository
        extends JpaRepository<RiskHistory, Long> {

    List<RiskHistory> findByRiskIssueIdOrderByChangedAtAscIdAsc(
            Long riskIssueId
    );
}