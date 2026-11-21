package com.example.backend.domain.AI.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiSceneCandidates {

    private AiSceneCandidates() {}

    public static List<JsonNode> from(
            JsonNode scene,
            ObjectMapper objectMapper,
            HttpStatus errorStatus
    ) {
        JsonNode detections = scene.path("detections");

        if (!detections.isArray()) {
            throw new ResponseStatusException(
                    errorStatus,
                    "장면의 detections 배열이 필요합니다."
            );
        }

        List<JsonNode> candidates = new ArrayList<>();

        if (!detections.isEmpty()) {
            detections.forEach(candidates::add);
            return candidates;
        }

        JsonNode findings = scene.path("relations").path("findings");

        if (findings.isMissingNode() || findings.isNull()) {
            return candidates;
        }

        if (!findings.isArray()) {
            throw new ResponseStatusException(
                    errorStatus,
                    "relations.findings는 배열이어야 합니다."
            );
        }

        for (JsonNode finding : findings) {
            if (!"위험".equals(
                    finding.path("status").asText().trim()
            )) {
                continue;
            }

            String hazard = finding.path("hazard").asText("").trim();

            if (hazard.isEmpty()) {
                throw new ResponseStatusException(
                        errorStatus,
                        "규칙 위험의 hazard가 필요합니다."
                );
            }

            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("hazard", hazard);
            candidate.put("location", null);
            candidate.put(
                    "evidence",
                    "공간관계 규칙 "
                            + finding.path("code").asText("")
                            + "에서 위험으로 판단: "
                            + hazard
            );
            candidate.put("bbox", finding.get("bbox"));
            candidate.put("confidence", null);
            candidate.put("source", "spatial");

            candidates.add(objectMapper.readTree(
                    objectMapper.writeValueAsString(candidate)
            ));
        }

        return candidates;
    }
}