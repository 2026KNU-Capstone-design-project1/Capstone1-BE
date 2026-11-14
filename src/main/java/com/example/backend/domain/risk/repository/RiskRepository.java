package com.example.backend.domain.risk.repository;

import com.example.backend.domain.risk.entity.RiskIssue;

import java.util.List;

import com.example.backend.domain.worker.dto.RiskSum;
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

    @Query(
            value = "SELECT name FROM facilities WHERE id = :facilityId",
            nativeQuery = true
    )
    String findFacilityName(@Param("facilityId") Long facilityId);

    interface FacilityContext
    {
    String getSiteName();
    String getZoneName();
}

    @Query(value = """
            SELECT s.name AS "siteName", z.name AS "zoneName"
            FROM facilities f
            LEFT JOIN zones z ON z.id = f.zone_id
            LEFT JOIN sites s ON s.id = z.site_id
            WHERE f.id = :facilityId
            """, nativeQuery = true)
    FacilityContext findFacilityContext(@Param("facilityId") Long facilityId);

    interface RelatedVideo {
        Long getVideoId();
        String getVideoUrl();
    }

    @Query(value = """
            SELECT DISTINCT v.id AS "videoId", v.video_url AS "videoUrl"
            FROM risk_detections d
            JOIN risk_frames f ON f.id = d.risk_frame_id
            JOIN videos v ON v.id = f.video_id
            WHERE d.risk_issue_id = :riskId
            ORDER BY v.id
            """, nativeQuery = true)
    List<RelatedVideo> findRelatedVideos(@Param("riskId") Long riskId);

    @Query(value = """
    SELECT count(id) as openedCount from risks_issue
        where status='OPEN'
        and user_id=(select id from users where login_id=:loginId);
        """, nativeQuery = true)
    int countOpenedIssues(@Param("loginId") String loginId);

    @Query(value = """
    SELECT count(id) as openedCount from risks_issue
        where status='IN_PROGRESS'
        and user_id=(select id from users where login_id=:loginId);
        """, nativeQuery = true)
    int countInProgressIssues(@Param("loginId") String loginId);

    @Query(value = """
    SELECT count(id) as openedCount from risks_issue
        where status='RESOLVED'
        and user_id=(select id from users where login_id=:loginId);
        """, nativeQuery = true)
    int countResolvedIssues(@Param("loginId") String loginId);

    @Query(value = """
    SELECT
        r.id,
        r.hazard,
        z.name AS zoneName,
        f.name AS facilityName,
        r.risk_level AS level
    FROM risks_issue r
    JOIN facilities f ON f.id = r.facility_id
    JOIN zones z ON z.id = f.zone_id

    JOIN LATERAL (
        SELECT h.status, h.cycle
        FROM risk_history h
        WHERE h.risk_issue_id = r.id
        ORDER BY h.cycle DESC, h.created_at DESC, h.id DESC
        LIMIT 1
    ) latest ON TRUE

    LEFT JOIN LATERAL (
        SELECT MIN(h.created_at) AS first_open_at
        FROM risk_history h
        WHERE h.risk_issue_id = r.id
          AND h.cycle = latest.cycle
          AND h.status = 'OPEN'
          AND h.event_type = 'DETECTED'
    ) opened ON TRUE
                 
    WHERE r.user_id=(select id from users where login_id=:loginId)

    ORDER BY
        CASE latest.status
            WHEN 'OPEN' THEN 1
            WHEN 'IN_PROGRESS' THEN 2
            WHEN 'RESOLVED' THEN 3
            ELSE 4
        END ASC,

        CASE r.risk_level
            WHEN 'HIGH' THEN 1
            WHEN 'MEDIUM' THEN 2
            WHEN 'LOW' THEN 3
            ELSE 4
        END ASC,

        CASE
            WHEN latest.status = 'OPEN'
            THEN opened.first_open_at
        END ASC NULLS LAST,

        r.id ASC

    LIMIT 1
    """, nativeQuery = true)
    RiskSum findMostPriorRisk(@Param("loginId") String loginId);

    @Query(value = """
    select r.id, r.hazard, z.name as zoneName, f.name as facilityName, r.risk_level as level
        from risks_issue r
        join facilities f on f.id=r.facility_id
        join zones z on z.id=f.zone_id
        join sites s on s.id=z.site_id
        where r.status in ('OPEN', 'IN_PROGRESS')
            and r.user_id=(select id from users where login_id=:loginId)
        order by r.created_at desc
        limit 3
        """, nativeQuery = true)
    List<RiskSum> find3Risks(@Param("loginId") String loginId);
}