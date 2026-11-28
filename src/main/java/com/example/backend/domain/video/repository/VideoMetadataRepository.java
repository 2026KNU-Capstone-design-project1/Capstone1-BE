package com.example.backend.domain.video.repository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class VideoMetadataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public VideoMetadataRepository(
            NamedParameterJdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    public record Metadata(
            Long videoId,
            String fileName,
            Long zoneId,
            Long facilityId,
            String area,
            OffsetDateTime recordedAt,
            String recordedAtSource,
            String analysisError
    ) {}

    public VideoRepository.Target findTarget(
            String loginId,
            Long zoneId,
            Long facilityId
    ) {
        List<VideoRepository.Target> targets = jdbc.query(
                """
                SELECT u.id AS user_id,
                       s.id AS site_id,
                       z.id AS zone_id,
                       z.name AS zone_name,
                       f.id AS facility_id
                FROM zones z
                JOIN sites s ON s.id = z.site_id
                JOIN users u ON u.login_id = :loginId
                JOIN facilities f ON f.zone_id = z.id
                WHERE z.id = :zoneId
                  AND (
                      COALESCE(:facilityId, z.analysis_facility_id) IS NULL
                      OR f.id = COALESCE(
                          :facilityId, z.analysis_facility_id
                      )
                  )
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
                (rs, rowNum) -> new VideoRepository.Target(
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
                    "접근 가능한 구역과 해당 구역의 분석 시설물을 확인해주세요."
            );
        }

        if (targets.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "해당 구역의 기본 분석 시설물을 지정해주세요."
            );
        }

        return targets.getFirst();
    }

    public Long insert(
            VideoRepository.Target target,
            String objectKey,
            String fileName,
            String totalTime,
            OffsetDateTime recordedAt,
            String recordedAtSource
    ) {
        return jdbc.queryForObject(
                """
                INSERT INTO videos (
                    site_id,
                    video_url,
                    user_id,
                    uploaded_at,
                    status,
                    total_time,
                    file_name,
                    zone_id,
                    facility_id,
                    recorded_at,
                    recorded_at_source
                )
                VALUES (
                    :siteId,
                    :objectKey,
                    :userId,
                    NOW(),
                    'PENDING',
                    :totalTime,
                    :fileName,
                    :zoneId,
                    :facilityId,
                    :recordedAt,
                    :recordedAtSource
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("siteId", target.siteId())
                        .addValue("objectKey", objectKey)
                        .addValue("userId", target.userId())
                        .addValue("totalTime", totalTime)
                        .addValue("fileName", fileName)
                        .addValue("zoneId", target.zoneId())
                        .addValue("facilityId", target.facilityId())
                        .addValue("recordedAt", recordedAt)
                        .addValue(
                                "recordedAtSource",
                                recordedAtSource
                        ),
                Long.class
        );
    }

    public Map<Long, Metadata> findAll(List<Long> videoIds) {
        Map<Long, Metadata> result = new LinkedHashMap<>();

        if (videoIds.isEmpty()) {
            return result;
        }

        jdbc.query(
                """
                SELECT v.id,
                       v.file_name,
                       v.zone_id,
                       v.facility_id,
                       v.recorded_at,
                       v.recorded_at_source,
                       v.analysis_error,
                       z.name AS area
                FROM videos v
                LEFT JOIN zones z ON z.id = v.zone_id
                WHERE v.id IN (:ids)
                """,
                Map.of("ids", videoIds),
                (rs, rowNum) -> {
                    Metadata metadata = new Metadata(
                            rs.getLong("id"),
                            rs.getString("file_name"),
                            rs.getObject("zone_id", Long.class),
                            rs.getObject("facility_id", Long.class),
                            rs.getString("area"),
                            rs.getObject(
                                    "recorded_at",
                                    OffsetDateTime.class
                            ),
                            rs.getString("recorded_at_source"),
                            rs.getString("analysis_error")
                    );

                    result.put(metadata.videoId(), metadata);
                    return metadata;
                }
        );

        return result;
    }

    public void clearError(Long videoId) {
        jdbc.update(
                "UPDATE videos SET analysis_error = NULL WHERE id = :id",
                Map.of("id", videoId)
        );
    }

    public void fail(Long videoId, String message) {
        jdbc.update(
                """
                UPDATE videos
                SET status = 'FAILED',
                    analysis_error = :message
                WHERE id = :id
                  AND status IN ('PENDING', 'PROCESSING')
                """,
                Map.of(
                        "id", videoId,
                        "message", message
                )
        );
    }
}