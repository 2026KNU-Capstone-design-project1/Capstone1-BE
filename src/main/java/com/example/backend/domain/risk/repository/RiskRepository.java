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
}