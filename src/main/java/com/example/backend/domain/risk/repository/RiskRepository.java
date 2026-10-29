package com.example.backend.domain.risk.repository;

import com.example.backend.domain.risk.entity.RiskIssue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRepository extends JpaRepository<RiskIssue, Long>
{
    Page<RiskIssue> findByFacilityId(
            Long facilityId,
            Pageable pageable
    );
}