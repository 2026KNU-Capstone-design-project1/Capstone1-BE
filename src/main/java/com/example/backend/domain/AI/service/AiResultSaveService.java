package com.example.backend.domain.AI.service;

import com.example.backend.domain.AI.dto.AiRequests;
import com.example.backend.domain.history.entity.RiskHistory;
import com.example.backend.domain.report.entity.Report;
import com.example.backend.domain.report.entity.ReportRiskIssue;
import com.example.backend.domain.report.repository.ReportRepository;
import com.example.backend.domain.report.repository.ReportRiskIssueRepository;
import com.example.backend.domain.risk.entity.RiskIssue;
import com.example.backend.domain.risk.entity.RiskLevel;
import com.example.backend.domain.risk.repository.RiskHistoryRepository;
import com.example.backend.domain.risk.repository.RiskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AiResultSaveService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RiskRepository riskRepository;
    private final RiskHistoryRepository historyRepository;
    private final ReportRepository reportRepository;
    private final ReportRiskIssueRepository linkRepository;

    public AiResultSaveService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            RiskRepository riskRepository,
            RiskHistoryRepository historyRepository,
            ReportRepository reportRepository,
            ReportRiskIssueRepository linkRepository
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.riskRepository = riskRepository;
        this.historyRepository = historyRepository;
        this.reportRepository = reportRepository;
        this.linkRepository = linkRepository;
    }

    public record SavedItem(
            Long frameId,
            Long riskId,
            Long reportId,
            boolean alreadySaved
    ) {}

    public record SaveResult(
            boolean alreadySaved,
            JsonNode items
    ) {}

    @Transactional(readOnly = true)
    public Map<String, Object> validateTarget(
            AiRequests.AnalyzeVideo request,
            String loginId
    ) {
        String sql = """
                SELECT s.id, s.name
                FROM videos v
                JOIN facilities f ON f.id = :facilityId
                JOIN zones z ON z.id = f.zone_id
                JOIN sites s ON s.id = z.site_id
                JOIN users u ON u.login_id = :loginId
                WHERE v.id = :videoId
                  AND v.site_id = s.id
                  AND (s.user_id = u.id OR u.site_id = s.id)
                """;

        List<Map<String, Object>> sites = jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("videoId", request.videoId())
                        .addValue("facilityId", request.facilityId())
                        .addValue("loginId", loginId),
                (rs, rowNum) -> {
                    Map<String, Object> site = new LinkedHashMap<>();
                    site.put("site_id", Long.toString(rs.getLong("id")));
                    site.put("site_name", rs.getString("name"));
                    return site;
                }
        );

        if (sites.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "영상과 시설물이 같은 접근 가능한 현장에 등록되어 있어야 합니다."
            );
        }

        return sites.getFirst();
    }

    @Transactional
    public SaveResult save(
            AiRequests.AnalyzeVideo request,
            String loginId,
            JsonNode preprocessing,
            List<Map<String, Object>> assessRequests,
            List<JsonNode> assessmentResults
    ) {
        validateTarget(request, loginId);

        jdbc.query(
                "SELECT pg_advisory_xact_lock(:videoId)",
                Map.of("videoId", request.videoId()),
                rs -> {
                    return null;
                }
        );

        JsonNode scenes = preprocessing.path("results");

        if (!scenes.isArray()
                || scenes.size() != assessmentResults.size()
                || scenes.size() != assessRequests.size()) {
            throw invalidResponse(
                    "전처리 장면 수와 평가 응답 수가 다릅니다."
            );
        }

        List<SavedItem> savedItems = new ArrayList<>();

        for (int index = 0; index < scenes.size(); index++) {
            JsonNode scene = scenes.get(index);
            JsonNode response = assessmentResults.get(index);

            if (!request.videoId().toString().equals(
                    scene.path("video_id").asText()
            )) {
                throw invalidResponse(
                        "장면의 video_id가 요청한 영상과 다릅니다."
                );
            }

            String expectedId = String.valueOf(
                    assessRequests.get(index).get("assessment_id")
            );

            if (!expectedId.equals(text(response, "assessment_id"))) {
                throw invalidResponse(
                        "LLM 평가 ID가 요청과 다릅니다."
                );
            }

            JsonNode detections = scene.path("detections");
            JsonNode assessments = response.path("assessments");

            if (!detections.isArray()
                    || !assessments.isArray()
                    || detections.size() != assessments.size()) {
                throw invalidResponse(
                        "감지 수와 개별 평가 수가 다릅니다."
                );
            }

            if (detections.isEmpty()) {
                continue;
            }

            List<JsonNode> remaining = new ArrayList<>();
            assessments.forEach(remaining::add);

            for (JsonNode detection : detections) {
                JsonNode assessment = matchAssessment(
                        detection,
                        remaining
                );

                remaining.remove(assessment);

                if ("관측상 정상".equals(
                        text(assessment, "violation_status")
                )) {
                    continue;
                }

                Long frameId = saveFrame(
                        request.videoId(),
                        scene
                );

                savedItems.add(saveRisk(
                        request,
                        scene,
                        detection,
                        assessment,
                        frameId
                ));
            }
        }

        boolean alreadySaved = !savedItems.isEmpty()
                && savedItems.stream().allMatch(SavedItem::alreadySaved);

        return new SaveResult(
                alreadySaved,
                objectMapper.readTree(
                        objectMapper.writeValueAsString(savedItems)
                )
        );
    }

    private Long saveFrame(
            Long videoId,
            JsonNode scene
    ) {
        long frameIndex = requiredTime(scene, "frame_index");
        long frameTimeMs = requiredTime(scene, "frame_time_ms");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("videoId", videoId)
                .addValue("frameIndex", frameIndex)
                .addValue("frameTimeMs", frameTimeMs)
                .addValue("imageUrl", text(scene, "image_url"));

        List<Long> existing = jdbc.query(
                """
                SELECT id
                FROM risk_frames
                WHERE video_id = :videoId
                  AND "index" = :frameIndex
                ORDER BY id
                LIMIT 1
                """,
                params,
                (rs, rowNum) -> rs.getLong("id")
        );

        if (!existing.isEmpty()) {
            return existing.getFirst();
        }

        return jdbc.queryForObject(
                """
                INSERT INTO risk_frames (
                    image_url,
                    frame_time_ms,
                    "index",
                    created_at,
                    video_id
                )
                VALUES (
                    :imageUrl,
                    :frameTimeMs,
                    :frameIndex,
                    NOW(),
                    :videoId
                )
                RETURNING id
                """,
                params,
                Long.class
        );
    }

    private SavedItem findExisting(
            Long videoId,
            Long facilityId,
            Long frameId,
            String hazard,
            Map<String, Double> bbox
    ) {
        String sql = """
                SELECT d.risk_frame_id AS frame_id,
                       r.id AS risk_id,
                       (
                           SELECT MIN(link.report_id)
                           FROM report_risk_issues link
                           JOIN reports report ON report.id = link.report_id
                           WHERE link.risk_issue_id = r.id
                             AND report.report_type = 'INDIVIDUAL'
                       ) AS report_id
                FROM risk_detections d
                JOIN risk_frames frame ON frame.id = d.risk_frame_id
                JOIN risks_issue r ON r.id = d.risk_issue_id
                WHERE frame.video_id = :videoId
                  AND d.risk_frame_id = :frameId
                  AND d.facility_id = :facilityId
                  AND r.facility_id = :facilityId
                  AND r.hazard = :hazard
                  AND d.bbox IS NOT DISTINCT FROM CAST(:bbox AS jsonb)
                ORDER BY d.id
                LIMIT 1
                """;

        List<SavedItem> existing = jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("videoId", videoId)
                        .addValue("facilityId", facilityId)
                        .addValue("frameId", frameId)
                        .addValue("hazard", hazard)
                        .addValue(
                                "bbox",
                                objectMapper.writeValueAsString(bbox)
                        ),
                (rs, rowNum) -> new SavedItem(
                        rs.getLong("frame_id"),
                        rs.getLong("risk_id"),
                        rs.getObject("report_id", Long.class),
                        true
                )
        );

        if (existing.isEmpty()) {
            return null;
        }

        SavedItem item = existing.getFirst();

        if (item.reportId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "같은 감지의 위험은 있지만 연결된 개별 보고서가 없습니다. 위험 ID: "
                            + item.riskId()
            );
        }

        return item;
    }

    private SavedItem saveRisk(
            AiRequests.AnalyzeVideo request,
            JsonNode scene,
            JsonNode detection,
            JsonNode assessment,
            Long frameId
    ) {
        JsonNode reportBody = assessment.path("report");

        String hazard = firstText(
                text(assessment, "hazard"),
                text(detection, "hazard")
        );

        if (hazard == null) {
            throw invalidResponse("위험 내용이 없습니다.");
        }

        String location = firstText(
                text(assessment, "location"),
                text(detection, "location")
        );

        String riskType = firstText(
                text(reportBody, "예상 재해유형"),
                text(assessment, "accident_type")
        );

        long representativeTime = requiredTime(
                scene,
                "frame_time_ms"
        );

        long start = optionalTime(
                detection,
                "start_time_ms",
                representativeTime
        );

        long end = optionalTime(
                detection,
                "end_time_ms",
                representativeTime
        );

        if (end < start) {
            throw invalidResponse(
                    "감지 종료 시점이 시작 시점보다 빠릅니다."
            );
        }

        Map<String, Double> bbox = readBbox(
                detection.get("bbox")
        );

        SavedItem existing = findExisting(
                request.videoId(),
                request.facilityId(),
                frameId,
                hazard,
                bbox
        );

        if (existing != null) {
            return existing;
        }

        JsonNode confidence = detection.get("confidence");

        if (confidence == null
                || !confidence.isNumber()
                || confidence.asDouble() < 0
                || confidence.asDouble() > 1) {
            throw invalidResponse(
                    "confidence는 0~1 숫자여야 합니다."
            );
        }

        RiskIssue risk = new RiskIssue(
                request.facilityId(),
                riskType,
                hazard,
                parseRiskLevel(firstText(
                        text(assessment, "risk_level"),
                        text(reportBody, "위험도")
                )),
                start,
                end,
                text(detection, "evidence"),
                location
        );

        risk.updateRepresentativeImage(
                text(scene, "image_url"),
                bbox
        );

        RiskIssue savedRisk = riskRepository.save(risk);

        historyRepository.save(new RiskHistory(
                savedRisk.getId(),
                savedRisk.getStatus(),
                savedRisk.getCreatedAt(),
                "DETECTED",
                1L
        ));

        jdbc.update(
                """
                INSERT INTO risk_detections (
                    facility_id,
                    bbox,
                    confidence,
                    created_at,
                    risk_issue_id,
                    risk_frame_id
                )
                VALUES (
                    :facilityId,
                    CAST(:bbox AS jsonb),
                    :confidence,
                    NOW(),
                    :riskId,
                    :frameId
                )
                """,
                new MapSqlParameterSource()
                        .addValue("facilityId", request.facilityId())
                        .addValue(
                                "bbox",
                                objectMapper.writeValueAsString(bbox)
                        )
                        .addValue("confidence", confidence.asDouble())
                        .addValue("riskId", savedRisk.getId())
                        .addValue("frameId", frameId)
        );

        String taskName = request.workInfo() == null
                ? null
                : valueText(request.workInfo().get("work_name"));

        Report report = reportRepository.save(new Report(
                taskName,
                location,
                riskType,
                firstText(
                        text(reportBody, "개선대책"),
                        text(reportBody, "improvement")
                ),
                firstText(
                        text(reportBody, "안전대책"),
                        text(reportBody, "safety_measures")
                ),
                firstText(
                        text(reportBody, "법적사항"),
                        text(reportBody, "legal_basis")
                )
        ));

        linkRepository.save(new ReportRiskIssue(
                report.getId(),
                savedRisk.getId()
        ));

        return new SavedItem(
                frameId,
                savedRisk.getId(),
                report.getId(),
                false
        );
    }

    private JsonNode matchAssessment(
            JsonNode detection,
            List<JsonNode> assessments
    ) {
        String hazard = text(detection, "hazard");

        List<JsonNode> matches = assessments.stream()
                .filter(item -> hazard != null
                        && hazard.equals(text(item, "hazard")))
                .toList();

        if (matches.size() == 1) {
            return matches.getFirst();
        }

        Map<String, Double> expectedBbox = readBbox(
                detection.get("bbox")
        );

        List<JsonNode> exactMatches = matches.stream()
                .filter(item -> Objects.equals(
                        expectedBbox,
                        readBbox(item.get("bbox"))
                ))
                .toList();

        if (exactMatches.size() != 1) {
            throw invalidResponse(
                    "감지 결과와 LLM 평가를 위험명·좌표로 연결할 수 없습니다."
            );
        }

        return exactMatches.getFirst();
    }

    private Map<String, Double> readBbox(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (!node.isObject()) {
            throw invalidResponse(
                    "bbox는 JSON 객체여야 합니다."
            );
        }

        Map<String, Double> result = new LinkedHashMap<>();

        for (String key : List.of("x", "y", "width", "height")) {
            JsonNode value = node.get(key);

            if (value == null
                    || !value.isNumber()
                    || value.asDouble() < 0
                    || value.asDouble() > 1) {
                throw invalidResponse(
                        "bbox의 " + key + " 값은 0~1 숫자여야 합니다."
                );
            }

            result.put(key, value.asDouble());
        }

        return result;
    }

    private RiskLevel parseRiskLevel(String value) {
        if (value == null) {
            return null;
        }

        return switch (value.trim()) {
            case "상", "HIGH" -> RiskLevel.HIGH;
            case "중", "MEDIUM" -> RiskLevel.MEDIUM;
            case "하", "LOW" -> RiskLevel.LOW;
            case "미확정", "추가 확인 필요", "UNDETERMINED" -> null;
            default -> throw invalidResponse(
                    "지원하지 않는 위험 등급: " + value
            );
        };
    }

    private long requiredTime(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);

        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.asLong() < 0) {
            throw invalidResponse(
                    field + "는 0 이상의 정수여야 합니다."
            );
        }

        return value.asLong();
    }

    private long optionalTime(
            JsonNode node,
            String field,
            long fallback
    ) {
        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return fallback;
        }

        return requiredTime(node, field);
    }

    private String text(
            JsonNode node,
            String field
    ) {
        if (node == null || !node.isObject()) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        String result = value.isString()
                ? value.asText()
                : objectMapper.writeValueAsString(value);

        return result.isBlank() ? null : result;
    }

    private String valueText(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstText(
            String first,
            String second
    ) {
        return first == null || first.isBlank() ? second : first;
    }

    private ResponseStatusException invalidResponse(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                message
        );
    }
}