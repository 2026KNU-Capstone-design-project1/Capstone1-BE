package com.example.backend.domain.dashboard.repository;

import com.example.backend.domain.dashboard.dto.DashboardResponse.SiteItem;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DashboardUser(
            Long id,
            String name,
            String role
    ) {}

    public record RiskRow(
            Long id,
            Long facilityId,
            String facilityName,
            String engName,
            Long zoneId,
            String zoneName,
            String hazard,
            String riskType,
            String riskLevel,
            String status,
            String imageUrl,
            Long userId,
            String managerName,
            OffsetDateTime detectedAt,
            OffsetDateTime statusChangedAt
    ) {}

    public Optional<DashboardUser> findUser(String loginId) {
        String sql = """
                SELECT id, name, role
                FROM users
                WHERE login_id = :loginId
                """;

        return jdbc.query(
                sql,
                Map.of("loginId", loginId),
                (rs, rowNum) -> new DashboardUser(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("role")
                )
        ).stream().findFirst();
    }

    public List<SiteItem> findAccessibleSites(String loginId) {
        String sql = """
                SELECT s.id, s.name
                FROM sites s
                JOIN users u ON u.login_id = :loginId
                WHERE s.user_id = u.id
                   OR s.id = u.site_id
                ORDER BY s.id
                """;

        return jdbc.query(
                sql,
                Map.of("loginId", loginId),
                (rs, rowNum) -> new SiteItem(
                        rs.getLong("id"),
                        rs.getString("name")
                )
        );
    }

    public List<RiskRow> findActiveRisks(Long siteId) {
        String sql = """
                SELECT r.id,
                       r.facility_id,
                       f.name AS facility_name,
                       f.eng_name,
                       z.id AS zone_id,
                       z.name AS zone_name,
                       r.hazard,
                       r.risk_type,
                       r.risk_level,
                       r.status,
                       r.image_url,
                       r.user_id,
                       manager.name AS manager_name,
                       COALESCE(opened.detected_at, r.created_at) AS detected_at,
                       COALESCE(
                           CASE WHEN latest.status = r.status
                                THEN latest.created_at END,
                           r.created_at
                       ) AS status_changed_at
                FROM risks_issue r
                JOIN facilities f ON f.id = r.facility_id
                JOIN zones z ON z.id = f.zone_id
                LEFT JOIN users manager ON manager.id = r.user_id
                LEFT JOIN LATERAL (
                    SELECT h.status, h.created_at, COALESCE(h.cycle, 1) AS cycle
                    FROM risk_history h
                    WHERE h.risk_issue_id = r.id
                    ORDER BY COALESCE(h.cycle, 1) DESC,
                             h.created_at DESC NULLS LAST,
                             h.id DESC
                    LIMIT 1
                ) latest ON TRUE
                LEFT JOIN LATERAL (
                    SELECT MIN(h.created_at) AS detected_at
                    FROM risk_history h
                    WHERE h.risk_issue_id = r.id
                      AND COALESCE(h.cycle, 1) = COALESCE(latest.cycle, 1)
                      AND h.event_type IN ('DETECTED', 'REOPENED')
                ) opened ON TRUE
                WHERE z.site_id = :siteId
                  AND r.status IN ('OPEN', 'IN_PROGRESS')
                ORDER BY detected_at DESC NULLS LAST, r.id DESC
                """;

        return jdbc.query(
                sql,
                Map.of("siteId", siteId),
                (rs, rowNum) -> toRiskRow(rs)
        );
    }

    public BigDecimal findAverageResolutionDays(Long siteId) {
        String sql = """
                WITH cycles AS (
                    SELECT h.risk_issue_id,
                           COALESCE(h.cycle, 1) AS cycle,
                           MIN(h.created_at) FILTER (
                               WHERE h.event_type IN ('DETECTED', 'REOPENED')
                           ) AS opened_at,
                           MIN(h.created_at) FILTER (
                               WHERE h.status = 'RESOLVED'
                           ) AS resolved_at
                    FROM risk_history h
                    JOIN risks_issue r ON r.id = h.risk_issue_id
                    JOIN facilities f ON f.id = r.facility_id
                    JOIN zones z ON z.id = f.zone_id
                    WHERE z.site_id = :siteId
                    GROUP BY h.risk_issue_id, COALESCE(h.cycle, 1)
                )
                SELECT ROUND(
                    AVG(EXTRACT(EPOCH FROM (resolved_at - opened_at)) / 86400.0),
                    1
                )
                FROM cycles
                WHERE opened_at IS NOT NULL
                  AND resolved_at IS NOT NULL
                  AND resolved_at >= opened_at
                """;

        return jdbc.queryForObject(
                sql,
                Map.of("siteId", siteId),
                BigDecimal.class
        );
    }

    private RiskRow toRiskRow(ResultSet rs) throws SQLException {
        return new RiskRow(
                rs.getLong("id"),
                rs.getLong("facility_id"),
                rs.getString("facility_name"),
                rs.getString("eng_name"),
                rs.getLong("zone_id"),
                rs.getString("zone_name"),
                rs.getString("hazard"),
                rs.getString("risk_type"),
                rs.getString("risk_level"),
                rs.getString("status"),
                rs.getString("image_url"),
                rs.getObject("user_id", Long.class),
                rs.getString("manager_name"),
                rs.getObject("detected_at", OffsetDateTime.class),
                rs.getObject("status_changed_at", OffsetDateTime.class)
        );
    }
}