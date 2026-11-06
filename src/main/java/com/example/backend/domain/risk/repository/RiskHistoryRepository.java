package com.example.backend.domain.risk.repository;

import com.example.backend.domain.history.entity.RiskHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskHistoryRepository
        extends JpaRepository<RiskHistory, Long> {

    @org.springframework.data.jpa.repository.Query("""
            select coalesce(max(h.cycle), 1)
            from RiskHistory h
            where h.riskIssueId = :riskIssueId
            """)

    Long findCurrentCycle(
            @org.springframework.data.repository.query.Param("riskIssueId")
            Long riskIssueId
    );

    List<RiskHistory> findByRiskIssueIdOrderByChangedAtAscIdAsc(
            Long riskIssueId
    );
}