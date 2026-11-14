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
            join RiskIssue risk
              on risk.id = firstDetection.riskIssueId
            join Facility facility
              on facility.id = risk.facilityId
            join facility.zone zone
            join zone.site site
            join User managedUser
              on managedUser.id = site.userId
            where managedUser.loginId = :loginId
              and firstDetection.eventType = 'DETECTED'
              and exists (
                  select laterDetection.id
                  from RiskHistory laterDetection
                  where laterDetection.riskIssueId = firstDetection.riskIssueId
                    and laterDetection.eventType = 'DETECTED'
                    and laterDetection.id > firstDetection.id
              )
            """)
    long countRepeatedDetectionRisks(
            @Param("loginId") String loginId
    );

    @Query("""
            select count(distinct detection.riskIssueId)
            from RiskHistory detection
            join RiskIssue risk
              on risk.id = detection.riskIssueId
            join Facility facility
              on facility.id = risk.facilityId
            join facility.zone zone
            join zone.site site
            join User managedUser
              on managedUser.id = site.userId
            where managedUser.loginId = :loginId
              and detection.eventType = 'DETECTED'
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
            @Param("daysAgo") OffsetDateTime daysAgo,
            @Param("loginId") String loginId
    );

    @Query("""
            select count(distinct redetected.riskIssueId)
            from RiskHistory redetected
            join RiskIssue risk
              on risk.id = redetected.riskIssueId
            join Facility facility
              on facility.id = risk.facilityId
            join facility.zone zone
            join zone.site site
            join User managedUser
              on managedUser.id = site.userId
            where managedUser.loginId = :loginId
              and redetected.eventType = 'DETECTED'
              and exists (
                  select resolved.id
                  from RiskHistory resolved
                  where resolved.riskIssueId = redetected.riskIssueId
                    and resolved.status = com.example.backend.domain.risk.entity.RiskStatus.RESOLVED
                    and resolved.id < redetected.id
              )
            """)
    long countRedetectedAfterActionRisks(
            @Param("loginId") String loginId
    );

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
            join Facility facility on facility.id = risk.facilityId
            join facility.zone zone
            join zone.site site
            join User managedUser on managedUser.id = site.userId
            left join RiskHistory detection
                   on detection.riskIssueId = risk.id
                  and detection.eventType = 'DETECTED'
            where managedUser.loginId = :loginId
            group by risk.id,
                     risk.riskType,
                     risk.hazard,
                     zone.name,
                     facility.name,
                     risk.status
            """)
    List<RiskHistoryItemView> findHistoryItems(
            @Param("loginId") String loginId
    );

    @Query("""
            select history.riskIssueId as riskIssueId,
                   history.id as id,
                   history.status as status,
                   history.changedAt as changedAt,
                   history.eventType as eventType,
                   history.cycle as cycle
            from RiskHistory history
            join RiskIssue risk
              on risk.id = history.riskIssueId
            join Facility facility
              on facility.id = risk.facilityId
            join facility.zone zone
            join zone.site site
            join User managedUser
              on managedUser.id = site.userId
            where managedUser.loginId = :loginId
            order by history.riskIssueId, history.changedAt, history.id
            """)
    List<HistoryEventView> findHistoryEvents(
            @Param("loginId") String loginId
    );

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
