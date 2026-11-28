package com.example.backend.domain.video.repository;

import com.example.backend.domain.video.dto.VideoDtos.FolderItem;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class VideoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public VideoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Target(
            Long userId,
            Long siteId,
            Long zoneId,
            String zoneName,
            Long facilityId
    ) {}

    public record ZoneFacilityRow(
            Long zoneId,
            String zoneName,
            Long siteId,
            Long facilityId,
            String facilityName
    ) {}

    public record VideoRow(
            Long id,
            Long siteId,
            String objectKey,
            String status,
            String totalTime,
            OffsetDateTime uploadedAt,
            String thumbnailKey,
            long riskCount,
            long highCount,
            long unresolvedCount
    ) {}

    private static final String VISIBLE_VIDEOS = """
            SELECT v.*
            FROM videos v
            JOIN sites s ON s.id = v.site_id
            JOIN users u ON u.login_id = :loginId
            WHERE (s.user_id = u.id OR u.site_id = s.id)
              AND (:siteId IS NULL OR v.site_id = :siteId)
            """;

    public void checkSiteAccess(String loginId, Long siteId) {
        if (siteId == null) {
            return;
        }

        if (siteId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현장 ID는 양수여야 합니다."
            );
        }

        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM sites s
                JOIN users u ON u.login_id = :loginId
                WHERE s.id = :siteId
                  AND (s.user_id = u.id OR u.site_id = s.id)
                """,
                Map.of(
                        "loginId", loginId,
                        "siteId", siteId
                ),
                Long.class
        );

        if (count == null || count == 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 현장에 접근할 수 없습니다."
            );
        }
    }

    public List<ZoneFacilityRow> findUploadOptions(String loginId) {
        return jdbc.query(
                """
                SELECT z.id AS zone_id,
                       z.name AS zone_name,
                       z.site_id,
                       f.id AS facility_id,
                       f.name AS facility_name
                FROM zones z
                JOIN sites s ON s.id = z.site_id
                JOIN users u ON u.login_id = :loginId
                LEFT JOIN facilities f ON f.zone_id = z.id
                WHERE s.user_id = u.id
                   OR u.site_id = s.id
                ORDER BY z.id, f.id
                """,
                Map.of("loginId", loginId),
                (rs, rowNum) -> new ZoneFacilityRow(
                        rs.getLong("zone_id"),
                        rs.getString("zone_name"),
                        rs.getLong("site_id"),
                        rs.getObject("facility_id", Long.class),
                        rs.getString("facility_name")
                )
        );
    }

    public Target findTarget(
            String loginId,
            Long zoneId,
            Long facilityId
    ) {
        List<Target> targets = jdbc.query(
                """
                SELECT u.id AS user_id,
                       s.id AS site_id,
                       z.id AS zone_id,
                       z.name AS zone_name,
                       f.id AS facility_id
                FROM zones z
                JOIN sites s ON s.id = z.site_id
                JOIN facilities f ON f.zone_id = z.id
                JOIN users u ON u.login_id = :loginId
                WHERE z.id = :zoneId
                  AND (:facilityId IS NULL OR f.id = :facilityId)
                  AND (s.user_id = u.id OR u.site_id = s.id)
                ORDER BY f.id
                """,
                new MapSqlParameterSource()
                        .addValue("loginId", loginId)
                        .addValue("zoneId", zoneId)
                        .addValue(
                                "facilityId",
                                facilityId,
                                Types.BIGINT
                        ),
                (rs, rowNum) -> new Target(
                        rs.getLong("user_id"),
                        rs.getLong("site_id"),
                        rs.getLong("zone_id"),
                        rs.getString("zone_name"),
                        rs.getLong("facility_id")
                )
        );

        if (targets.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "접근 가능한 구역과 해당 구역의 시설물을 선택해주세요."
            );
        }

        if (targets.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "구역에 시설물이 여러 개 있습니다. facilityId를 전달해주세요."
            );
        }

        return targets.getFirst();
    }

    public Long insert(
            Target target,
            String objectKey,
            String totalTime
    ) {
        return jdbc.queryForObject(
                """
                INSERT INTO videos (
                    site_id,
                    video_url,
                    user_id,
                    uploaded_at,
                    status,
                    total_time
                )
                VALUES (
                    :siteId,
                    :objectKey,
                    :userId,
                    NOW(),
                    'PENDING',
                    :totalTime
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("siteId", target.siteId())
                        .addValue("objectKey", objectKey)
                        .addValue("userId", target.userId())
                        .addValue("totalTime", totalTime),
                Long.class
        );
    }

    public boolean startAnalysis(Long videoId) {
        return jdbc.update(
                """
                UPDATE videos
                SET status = 'PROCESSING'
                WHERE id = :videoId
                  AND status = 'PENDING'
                """,
                Map.of("videoId", videoId)
        ) == 1;
    }

    public boolean prepareRetry(Long videoId) {
        return jdbc.update(
                """
                UPDATE videos
                SET status = 'PENDING'
                WHERE id = :videoId
                  AND status = 'FAILED'
                """,
                Map.of("videoId", videoId)
        ) == 1;
    }

    public void complete(Long videoId) {
        jdbc.update(
                """
                UPDATE videos
                SET status = 'COMPLETED'
                WHERE id = :videoId
                  AND status = 'PROCESSING'
                """,
                Map.of("videoId", videoId)
        );
    }

    public void fail(Long videoId) {
        jdbc.update(
                """
                UPDATE videos
                SET status = 'FAILED'
                WHERE id = :videoId
                  AND status IN ('PENDING', 'PROCESSING')
                """,
                Map.of("videoId", videoId)
        );
    }

    public List<FolderItem> findFolders(
            String loginId,
            Long siteId
    ) {
        String sql = """
                WITH visible_videos AS (
                """ + VISIBLE_VIDEOS + """
                )
                SELECT (v.uploaded_at AT TIME ZONE 'Asia/Seoul')::date
                           AS folder_date,
                       COUNT(DISTINCT v.id) AS video_count,
                       COUNT(DISTINCT v.id) FILTER (
                           WHERE v.status = 'COMPLETED'
                       ) AS analyzed_count,
                       COUNT(DISTINCT v.id) FILTER (
                           WHERE v.status = 'FAILED'
                       ) AS failed_count,
                       COUNT(DISTINCT r.id) AS risk_count,
                       COUNT(DISTINCT r.id) FILTER (
                           WHERE r.risk_level = 'HIGH'
                       ) AS high_count,
                       COUNT(DISTINCT r.id) FILTER (
                           WHERE r.status IN ('OPEN', 'IN_PROGRESS')
                       ) AS unresolved_count
                FROM visible_videos v
                LEFT JOIN risk_frames frame ON frame.video_id = v.id
                LEFT JOIN risk_detections d
                  ON d.risk_frame_id = frame.id
                LEFT JOIN risks_issue r ON r.id = d.risk_issue_id
                WHERE v.uploaded_at IS NOT NULL
                GROUP BY folder_date
                ORDER BY folder_date DESC
                """;

        return jdbc.query(
                sql,
                visibleParams(loginId, siteId),
                (rs, rowNum) -> new FolderItem(
                        rs.getObject("folder_date", LocalDate.class),
                        rs.getLong("video_count"),
                        rs.getLong("analyzed_count"),
                        rs.getLong("failed_count"),
                        rs.getLong("risk_count"),
                        rs.getLong("high_count"),
                        rs.getLong("unresolved_count")
                )
        );
    }

    public List<VideoRow> findVideos(
            String loginId,
            Long siteId,
            LocalDate date,
            Long videoId
    ) {
        String sql = """
                WITH visible_videos AS (
                """ + VISIBLE_VIDEOS + """
                )
                SELECT v.id,
                       v.site_id,
                       v.video_url,
                       v.status,
                       v.total_time,
                       v.uploaded_at,
                       thumbnail.image_url AS thumbnail_key,
                       COALESCE(stats.risk_count, 0) AS risk_count,
                       COALESCE(stats.high_count, 0) AS high_count,
                       COALESCE(stats.unresolved_count, 0)
                           AS unresolved_count
                FROM visible_videos v
                LEFT JOIN LATERAL (
                    SELECT COUNT(DISTINCT r.id) AS risk_count,
                           COUNT(DISTINCT r.id) FILTER (
                               WHERE r.risk_level = 'HIGH'
                           ) AS high_count,
                           COUNT(DISTINCT r.id) FILTER (
                               WHERE r.status IN ('OPEN', 'IN_PROGRESS')
                           ) AS unresolved_count
                    FROM risk_frames frame
                    JOIN risk_detections d
                      ON d.risk_frame_id = frame.id
                    JOIN risks_issue r ON r.id = d.risk_issue_id
                    WHERE frame.video_id = v.id
                ) stats ON TRUE
                LEFT JOIN LATERAL (
                    SELECT frame.image_url
                    FROM risk_frames frame
                    WHERE frame.video_id = v.id
                      AND frame.image_url IS NOT NULL
                      AND frame.image_url <> ''
                    ORDER BY frame.frame_time_ms NULLS LAST, frame.id
                    LIMIT 1
                ) thumbnail ON TRUE
                WHERE (:videoId IS NULL OR v.id = :videoId)
                  AND (
                      :date IS NULL
                      OR (v.uploaded_at AT TIME ZONE 'Asia/Seoul')::date
                          = :date
                  )
                ORDER BY v.uploaded_at DESC NULLS LAST, v.id DESC
                """;

        return jdbc.query(
                sql,
                visibleParams(loginId, siteId)
                        .addValue("date", date, Types.DATE)
                        .addValue("videoId", videoId, Types.BIGINT),
                (rs, rowNum) -> new VideoRow(
                        rs.getLong("id"),
                        rs.getLong("site_id"),
                        rs.getString("video_url"),
                        rs.getString("status"),
                        rs.getString("total_time"),
                        rs.getObject(
                                "uploaded_at",
                                OffsetDateTime.class
                        ),
                        rs.getString("thumbnail_key"),
                        rs.getLong("risk_count"),
                        rs.getLong("high_count"),
                        rs.getLong("unresolved_count")
                )
        );
    }

    private MapSqlParameterSource visibleParams(
            String loginId,
            Long siteId
    ) {
        return new MapSqlParameterSource()
                .addValue("loginId", loginId)
                .addValue("siteId", siteId, Types.BIGINT);
    }
}