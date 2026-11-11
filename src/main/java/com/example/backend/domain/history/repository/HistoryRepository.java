package com.example.backend.domain.history.repository;

import com.example.backend.domain.risk.entity.RiskIssue;
import com.example.backend.domain.risk.entity.RiskStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface HistoryRepository extends Repository<RiskIssue, Long> {

    @Query("""
            select count(distinct firstDetection.riskIssueId)
            from RiskHistory firstDetection
            where firstDetection.eventType = 'DETECTED'
              and exists (
                  select laterDetection.id
                  from RiskHistory laterDetection
                  where laterDetection.riskIssueId = firstDetection.riskIssueId
                    and laterDetection.eventType = 'DETECTED'
                    and laterDetection.id > firstDetection.id
              )
            """)
    long countRepeatedDetectionRisks();

    @Query("""
            select count(distinct detection.riskIssueId)
            from RiskHistory detection
            where detection.eventType = 'DETECTED'
              and detection.changedAt <= :daysAgo
              and not exists (
                  select resolved.id
                  from RiskHistory resolved
                  where resolved.riskIssueId = detection.riskIssueId
                    and resolved.status = com.example.backend.domain.risk.entity.RiskStatus.RESOLVED
                    and resolved.id > detection.id
              )
            """)
    long countLongTermUnresolvedRisks(
            @Param("daysAgo") OffsetDateTime daysAgo
    );

    @Query("""
            select count(distinct redetected.riskIssueId)
            from RiskHistory redetected
            where redetected.eventType = 'DETECTED'
              and exists (
                  select resolved.id
                  from RiskHistory resolved
                  where resolved.riskIssueId = redetected.riskIssueId
                    and resolved.status = com.example.backend.domain.risk.entity.RiskStatus.RESOLVED
                    and resolved.id < redetected.id
              )
            """)
    long countRedetectedAfterActionRisks();

    @Query("""
            select risk.id as riskId,
                   risk.riskType as riskType,
                   risk.hazard as hazard,
                   zone.name as zoneName,
                   facility.name as facilityName,
                   min(detection.changedAt) as firstDetectedAt,
                   max(detection.changedAt) as lastDetectedAt,
                   count(detection.id) as detectionCount,
                   risk.status as status
            from RiskIssue risk
            left join Facility facility on facility.id = risk.facilityId
            left join facility.zone zone
            left join RiskHistory detection
                   on detection.riskIssueId = risk.id
                  and detection.eventType = 'DETECTED'
            group by risk.id,
                     risk.riskType,
                     risk.hazard,
                     zone.name,
                     facility.name,
                     risk.status
            """)
    List<RiskHistoryItemView> findHistoryItems();

    @Query("""
            select history.riskIssueId as riskIssueId,
                   history.id as id,
                   history.status as status,
                   history.changedAt as changedAt,
                   history.eventType as eventType,
                   history.cycle as cycle
            from RiskHistory history
            order by history.riskIssueId, history.changedAt, history.id
            """)
    List<HistoryEventView> findHistoryEvents();

    interface RiskHistoryItemView {
        Long getRiskId();

        String getRiskType();

        String getHazard();

        String getZoneName();

        String getFacilityName();

        OffsetDateTime getFirstDetectedAt();

        OffsetDateTime getLastDetectedAt();

        long getDetectionCount();

        RiskStatus getStatus();
    }

    interface HistoryEventView {
        Long getRiskIssueId();

        Long getId();

        RiskStatus getStatus();

        OffsetDateTime getChangedAt();

        String getEventType();

        Long getCycle();
    }
}
