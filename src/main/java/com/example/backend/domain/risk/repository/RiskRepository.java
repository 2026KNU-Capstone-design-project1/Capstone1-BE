package com.example.backend.domain.risk.repository;

import com.example.backend.domain.risk.entity.RiskIssue;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskRepository extends JpaRepository<RiskIssue, Long>
{
    List<RiskIssue> findByFacilityId(
            Long facilityId,
            Sort sort
    );

    @Query(
            value= """
                   SELECT EXISTS(SELECT 1 FROM users WHERE id= :userId AND LOWER(role)='site_manager')""",
            nativeQuery = true
    )
    boolean existManager(@Param("userId") Long userId);

    @Query(
            value= "SELECT name FROM users WHERE id = :userId",
            nativeQuery = true
    )
    String findManagerName(@Param("userId")Long userId);
}