package com.example.backend.domain.AI.service;

import com.example.backend.domain.AI.dto.AiRequests;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.example.backend.global.storage.S3StorageService;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiIntegrationService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String preprocessBaseUrl;
    private final String llmBaseUrl;
    private final Duration requestTimeout;
    private final AiResultSaveService resultSaveService;
    private final S3StorageService storageService;

    public AiIntegrationService(
            ObjectMapper objectMapper,
            AiResultSaveService resultSaveService,
            S3StorageService storageService,

            @Value("${ai.preprocess-base-url:}")
            String preprocessBaseUrl,

            @Value("${ai.llm-base-url:}")
            String llmBaseUrl,

            @Value("${ai.request-timeout-seconds:300}")
            long requestTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.resultSaveService = resultSaveService;
        this.storageService = storageService;
        this.preprocessBaseUrl = preprocessBaseUrl;
        this.llmBaseUrl = llmBaseUrl;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Map<String, Object> analyzeVideo(
            AiRequests.AnalyzeVideo request,
            String loginId
    ) {
        Map<String, Object> siteInfo =
                resultSaveService.validateTarget(request, loginId);

        validateVideoUrl(request.videoUrl());

        URI preprocessUri = endpoint(
                preprocessBaseUrl,
                "/predict/video"
        );

        URI assessUri = endpoint(
                llmBaseUrl,
                "/assess"
        );

        Map<String, Object> preprocessRequest = new LinkedHashMap<>();
        preprocessRequest.put("video_id", request.videoId());
        preprocessRequest.put("video_url", request.videoUrl());
        preprocessRequest.put("include_relations", true);
        preprocessRequest.put("spec_only", false);

        JsonNode preprocessing = postJson(
                preprocessUri,
                preprocessRequest
        );

        JsonNode responseVideoId = preprocessing.get("video_id");

        if (responseVideoId == null
                || !responseVideoId.asText()
                .equals(request.videoId().toString())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "전처리 응답의 video_id가 요청과 다릅니다."
            );
        }

        List<Map<String, Object>> assessRequests = buildAssessRequests(
                preprocessing,
                siteInfo,
                request.workInfo(),
                HttpStatus.BAD_GATEWAY
        );

        List<JsonNode> assessmentResults = new ArrayList<>();

        for (Map<String, Object> assessRequest : assessRequests) {
            JsonNode assessment = postJson(
                    assessUri,
                    assessRequest
            );

            if (!assessment.path("assessments").isArray()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "LLM 응답에 assessments 배열이 없습니다."
                );
            }

            assessmentResults.add(assessment);
        }

        JsonNode storagePreprocessing = copyFramesToS3(
                request.videoId(),
                preprocessing,
                assessmentResults
        );

        AiResultSaveService.SaveResult saved = resultSaveService.save(
                request,
                loginId,
                storagePreprocessing,
                assessRequests,
                assessmentResults
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("video_id", request.videoId());
        response.put("scene_count", assessRequests.size());
        response.put("preprocessing", preprocessing);
        response.put("assess_requests", assessRequests);
        response.put("assessment_results", assessmentResults);
        response.put("already_saved", saved.alreadySaved());
        response.put("saved_items", saved.items());

        return response;
    }

    public Map<String, Object> previewAssess(AiRequests.PreviewAssess request) {
        List<Map<String, Object>> assessRequests = buildAssessRequests(
                request.preprocessing(),
                request.siteInfo(),
                request.workInfo(),
                HttpStatus.BAD_REQUEST
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scene_count", assessRequests.size());
        response.put("assess_requests", assessRequests);

        return response;
    }

    private List<Map<String, Object>> buildAssessRequests(
            JsonNode preprocessing,
            Map<String, Object> siteInfo,
            Map<String, Object> workInfo,
            HttpStatus errorStatus
    ) {
        JsonNode results = preprocessing.get("results");
        if (results == null || !results.isArray()) {
            throw new ResponseStatusException(
                    errorStatus,
                    "전처리 결과에 results 배열이 필요합니다."
            );
        }

        List<Map<String, Object>> requests = new ArrayList<>();
        String batchId = UUID.randomUUID().toString();

        int sceneIndex = 0;

        for (JsonNode scene : results) {
            sceneIndex++;

            if (!"video".equals(scene.path("source_type").asText())) {
                throw new ResponseStatusException(
                        errorStatus,
                        "현재 연동은 source_type이 video인 결과를 처리합니다."
                );
            }

            JsonNode videoId = scene.get("video_id");
            JsonNode detections = scene.get("detections");

            if (videoId == null || videoId.isNull()) {
                throw new ResponseStatusException(
                        errorStatus,
                        "장면의 video_id가 필요합니다."
                );
            }

            if (detections == null || !detections.isArray()) {
                throw new ResponseStatusException(
                        errorStatus,
                        "장면의 detections 배열이 필요합니다."
                );
            }

            JsonNode batchVideoId = preprocessing.get("video_id");
            if (batchVideoId != null
                    && !batchVideoId.isNull()
                    && !batchVideoId.asText().equals(videoId.asText())) {
                throw new ResponseStatusException(
                        errorStatus,
                        "장면의 video_id와 최상위 video_id가 다릅니다."
                );
            }

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("source_type", "video");
            source.put("photo_id", scene.get("photo_id"));
            source.put("video_id", videoId);
            source.put("frame_index", scene.get("frame_index"));
            source.put("frame_time_ms", scene.get("frame_time_ms"));
            source.put("image_url", scene.get("image_url"));

            Map<String, Object> assessRequest = new LinkedHashMap<>();
            assessRequest.put(
                    "assessment_id",
                    "RA-" + batchId + "-" + sceneIndex
            );
            assessRequest.put("site_info", siteInfo);
            assessRequest.put("work_info", workInfo);
            assessRequest.put("source", source);
            assessRequest.put("detections", detections);
            JsonNode relations = scene.get("relations");
            if (relations != null && !relations.isNull())
            {
                assessRequest.put("relations", relations);
            }

            requests.add(assessRequest);
        }

        return requests;
    }

    private JsonNode copyFramesToS3(
            Long videoId,
            JsonNode preprocessing,
            List<JsonNode> assessmentResults
    ) {
        JsonNode copied = objectMapper.readTree(
                objectMapper.writeValueAsString(preprocessing)
        );
        JsonNode scenes = copied.path("results");

        if (!scenes.isArray()
                || scenes.size() != assessmentResults.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "대표 이미지 저장 대상과 평가 결과 수가 다릅니다."
            );
        }

        for (int index = 0; index < scenes.size(); index++) {
            JsonNode scene = scenes.get(index);
            JsonNode assessments = assessmentResults.get(index)
                    .path("assessments");

            if (!scene.isObject() || !assessments.isArray()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "대표 이미지 저장 대상 형식이 올바르지 않습니다."
                );
            }

            boolean needsImage = false;

            for (JsonNode assessment : assessments) {
                if (!"관측상 정상".equals(
                        assessment.path("violation_status").asText()
                )) {
                    needsImage = true;
                    break;
                }
            }

            if (!needsImage) {
                continue;
            }

            JsonNode imageNode = scene.get("image_url");

            if (imageNode == null
                    || imageNode.isNull()
                    || imageNode.asText().isBlank()) {
                continue;
            }

            JsonNode frameIndex = scene.get("frame_index");

            if (frameIndex == null
                    || !frameIndex.isIntegralNumber()
                    || !frameIndex.canConvertToLong()
                    || frameIndex.asLong() < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "대표 이미지의 frame_index가 올바르지 않습니다."
                );
            }

            String objectKey = downloadAndSaveFrame(
                    videoId,
                    frameIndex.asLong(),
                    imageNode.asText()
            );

            ((ObjectNode) scene).put("image_url", objectKey);
        }

        return copied;
    }

    private String downloadAndSaveFrame(
            Long videoId,
            long frameIndex,
            String imageUrl
    ) {
        URI imageUri;

        try {
            imageUri = URI.create(imageUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 대표 이미지 URL 형식이 올바르지 않습니다.",
                    exception
            );
        }

        URI allowedOrigin = endpoint(preprocessBaseUrl, "");

        if (imageUri.getHost() == null
                || imageUri.getUserInfo() != null
                || !allowedOrigin.getScheme().equalsIgnoreCase(
                imageUri.getScheme()
        )
                || !allowedOrigin.getHost().equalsIgnoreCase(
                imageUri.getHost()
        )
                || allowedOrigin.getPort() != imageUri.getPort()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 대표 이미지는 설정된 전처리 서버 주소여야 합니다."
            );
        }

        HttpRequest imageRequest = HttpRequest.newBuilder(imageUri)
                .timeout(requestTimeout)
                .header("Accept", "image/jpeg, image/png, image/webp")
                .GET()
                .build();

        try {
            HttpResponse<java.io.InputStream> imageResponse =
                    httpClient.send(
                            imageRequest,
                            HttpResponse.BodyHandlers.ofInputStream()
                    );

            try (java.io.InputStream input = imageResponse.body()) {
                if (imageResponse.statusCode() != 200) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "AI 대표 이미지 다운로드 실패: HTTP "
                                    + imageResponse.statusCode()
                    );
                }

                String contentType = imageResponse.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .split(";", 2)[0]
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT);

                if (!List.of(
                        "image/jpeg",
                        "image/png",
                        "image/webp"
                ).contains(contentType)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "AI 대표 이미지 응답이 지원하는 이미지 형식이 아닙니다."
                    );
                }

                int maxBytes = 20 * 1024 * 1024;
                byte[] image = input.readNBytes(maxBytes + 1);

                return storageService.saveAiFrame(
                        videoId,
                        frameIndex,
                        image,
                        contentType
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 대표 이미지 저장이 중단됐습니다.",
                    exception
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 대표 이미지를 다운로드할 수 없습니다.",
                    exception
            );
        }
    }

    private JsonNode postJson(URI uri, Object body) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)
                ))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI API 호출 실패: " + uri.getPath()
                                + " / HTTP " + response.statusCode()
                );
            }

            JsonNode result;

            try {
                result = objectMapper.readTree(response.body());
            } catch (RuntimeException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI 응답을 JSON으로 읽을 수 없습니다.",
                        exception
                );
            }

            if (result == null || !result.isObject()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI 응답은 JSON 객체여야 합니다."
                );
            }

            return result;
        } catch (HttpTimeoutException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI 응답 대기 시간이 초과됐습니다.",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 요청 처리가 중단됐습니다.",
                    exception
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 서버에 연결할 수 없습니다.",
                    exception
            );
        }
    }

    private URI endpoint(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 서버 기본 주소를 환경변수에 설정해주세요: " + path
            );
        }

        URI base;

        try {
            base = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 서버 주소 형식이 올바르지 않습니다."
            );
        }

        if (base.getHost() == null
                || (!"http".equals(base.getScheme())
                && !"https".equals(base.getScheme()))
                || base.getQuery() != null
                || base.getFragment() != null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 서버 기본 주소 설정을 확인해주세요."
            );
        }

        return URI.create(baseUrl.trim().replaceAll("/+$", "") + path);
    }

    private void validateVideoUrl(String value) {
        URI uri;

        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "video_url 형식이 올바르지 않습니다."
            );
        }

        if (uri.getHost() == null
                || (!"https".equals(uri.getScheme())
                && !"s3".equals(uri.getScheme()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "video_url은 HTTPS 또는 S3 주소여야 합니다."
            );
        }
    }
}