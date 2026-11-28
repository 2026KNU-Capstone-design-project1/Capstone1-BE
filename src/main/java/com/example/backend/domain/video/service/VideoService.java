package com.example.backend.domain.video.service;

import com.example.backend.domain.AI.dto.AiRequests;
import com.example.backend.domain.AI.service.AiIntegrationService;
import com.example.backend.domain.video.dto.VideoDtos;
import com.example.backend.domain.video.repository.VideoMetadataRepository;
import com.example.backend.domain.video.repository.VideoRepository;
import com.example.backend.global.storage.S3StorageService;
import com.example.backend.global.storage.StorageObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

@Service
public class VideoService {

    private static final Logger log =
            LoggerFactory.getLogger(VideoService.class);

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final VideoRepository repository;
    private final VideoMetadataRepository metadataRepository;
    private final Mp4MetadataReader mediaReader;
    private final S3StorageService storageService;
    private final AiIntegrationService aiService;
    private final ThreadPoolTaskExecutor executor;

    public VideoService(
            VideoRepository repository,
            VideoMetadataRepository metadataRepository,
            Mp4MetadataReader mediaReader,
            S3StorageService storageService,
            AiIntegrationService aiService,
            @Qualifier("videoAnalysisExecutor")
            ThreadPoolTaskExecutor executor
    ) {
        this.repository = repository;
        this.metadataRepository = metadataRepository;
        this.mediaReader = mediaReader;
        this.storageService = storageService;
        this.aiService = aiService;
        this.executor = executor;
    }

    private record AnalysisTask(
            Long videoId,
            String loginId,
            Long siteId,
            Long facilityId,
            String zoneName,
            String objectKey
    ) {}

    public List<VideoDtos.ZoneOption> getUploadOptions(
            String loginId
    ) {
        Map<Long, VideoDtos.ZoneOption> zones =
                new LinkedHashMap<>();

        for (var row : repository.findUploadOptions(loginId)) {
            var zone = zones.computeIfAbsent(
                    row.zoneId(),
                    id -> new VideoDtos.ZoneOption(
                            id,
                            row.zoneName(),
                            row.siteId(),
                            new ArrayList<>()
                    )
            );

            if (row.facilityId() != null) {
                zone.facilities().add(
                        new VideoDtos.FacilityOption(
                                row.facilityId(),
                                row.facilityName()
                        )
                );
            }
        }

        return new ArrayList<>(zones.values());
    }

    public Long resolveZoneId(
            String loginId,
            Long zoneId,
            String areaId
    ) {
        if (zoneId != null) {
            if (zoneId <= 0) {
                throw badRequest("zoneId는 양수여야 합니다.");
            }
            return zoneId;
        }

        if (areaId == null || areaId.isBlank()) {
            throw badRequest("촬영 구역을 선택해주세요.");
        }

        String value = areaId.trim();

        if (value.matches("[1-9][0-9]*")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw badRequest("구역 ID가 올바르지 않습니다.");
            }
        }

        String zoneName = value.matches("AREA-[A-Z]")
                ? value.substring("AREA-".length()) + "구역"
                : value;

        List<Long> matches = repository.findUploadOptions(loginId)
                .stream()
                .filter(row -> zoneName.equals(row.zoneName()))
                .map(VideoRepository.ZoneFacilityRow::zoneId)
                .distinct()
                .toList();

        if (matches.size() != 1) {
            throw badRequest(
                    "구역을 유일하게 찾을 수 없습니다. 실제 zoneId를 전달해주세요."
            );
        }

        return matches.getFirst();
    }

    public VideoDtos.UploadResponse upload(
            String loginId,
            Long zoneId,
            Long facilityId,
            Integer durationSeconds,
            OffsetDateTime recordedAt,
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw badRequest("업로드할 영상을 선택해주세요.");
        }

        if (durationSeconds != null && durationSeconds <= 0) {
            throw badRequest("영상 길이는 0초보다 커야 합니다.");
        }

        String original = file.getOriginalFilename();

        if (original == null || original.isBlank()) {
            throw badRequest("파일명이 필요합니다.");
        }

        String fileName = original.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);

        if (fileName.isBlank() || fileName.length() > 255) {
            throw badRequest("파일명은 255자 이하여야 합니다.");
        }

        var target = metadataRepository.findTarget(
                loginId, zoneId, facilityId
        );

        var media = mediaReader.read(file);

        Integer seconds = durationSeconds != null
                ? durationSeconds
                : media.durationSeconds();

        OffsetDateTime capturedAt = recordedAt != null
                ? recordedAt
                : media.recordedAt();

        String source = recordedAt != null
                ? "USER_PROVIDED"
                : capturedAt != null
                ? "MEDIA_CREATION_TIME"
                : "UNKNOWN";

        var uploaded = storageService.upload(
                file, StorageObjectType.VIDEO
        );

        Long videoId;

        try {
            videoId = metadataRepository.insert(
                    target,
                    uploaded.objectKey(),
                    fileName,
                    formatDuration(seconds),
                    capturedAt,
                    source
            );
        } catch (RuntimeException exception) {
            try {
                storageService.deleteUploadedVideo(
                        uploaded.objectKey()
                );
            } catch (RuntimeException cleanupException) {
                log.error(
                        "DB 등록 실패 후 S3 정리 실패: key={}",
                        uploaded.objectKey(),
                        cleanupException
                );
            }
            throw exception;
        }

        boolean queued = enqueue(new AnalysisTask(
                videoId,
                loginId,
                target.siteId(),
                target.facilityId(),
                target.zoneName(),
                uploaded.objectKey()
        ));

        return new VideoDtos.UploadResponse(
                videoId,
                target.siteId(),
                queued ? "PENDING" : "FAILED"
        );
    }

    public VideoDtos.BatchResponse uploadBatch(
            String loginId,
            Long zoneId,
            Long facilityId,
            List<MultipartFile> files
    ) {
        if (files == null || files.isEmpty() || files.size() > 10) {
            throw badRequest("영상은 한 번에 1~10개까지 선택해주세요.");
        }

        metadataRepository.findTarget(loginId, zoneId, facilityId);

        List<VideoDtos.BatchItem> items = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                var result = upload(
                        loginId, zoneId, facilityId,
                        null, null, file
                );

                items.add(new VideoDtos.BatchItem(
                        file.getOriginalFilename(),
                        true,
                        result,
                        null
                ));
            } catch (Exception exception) {
                log.error("다중 영상 업로드 실패", exception);

                String message =
                        exception instanceof ResponseStatusException status
                                ? status.getReason()
                                : "영상 업로드 처리에 실패했습니다.";

                items.add(new VideoDtos.BatchItem(
                        file == null ? null : file.getOriginalFilename(),
                        false,
                        null,
                        message
                ));
            }
        }

        return new VideoDtos.BatchResponse(items);
    }

    public List<VideoDtos.FolderItem> getFolders(
            String loginId,
            Long siteId
    ) {
        repository.checkSiteAccess(loginId, siteId);
        return repository.findFolders(loginId, siteId);
    }

    public List<VideoDtos.VideoItem> getVideos(
            String loginId,
            Long siteId,
            LocalDate date
    ) {
        repository.checkSiteAccess(loginId, siteId);

        var rows = repository.findVideos(
                loginId, siteId, date, null
        );

        var metadata = metadataRepository.findAll(
                rows.stream()
                        .map(VideoRepository.VideoRow::id)
                        .toList()
        );

        return rows.stream()
                .map(row -> toItem(row, metadata.get(row.id())))
                .toList();
    }

    public VideoDtos.VideoItem getVideo(
            String loginId,
            Long videoId
    ) {
        var row = findVideo(loginId, videoId);
        var metadata = metadataRepository.findAll(List.of(videoId));

        return toItem(row, metadata.get(videoId));
    }

    public VideoDtos.UploadResponse retry(
            String loginId,
            Long videoId,
            VideoDtos.RetryRequest request
    ) {
        var video = findVideo(loginId, videoId);
        var metadata = metadataRepository
                .findAll(List.of(videoId)).get(videoId);

        Long zoneId = request != null && request.zoneId() != null
                ? request.zoneId()
                : metadata == null ? null : metadata.zoneId();

        if (zoneId == null) {
            throw badRequest("기존 영상은 재분석할 zoneId가 필요합니다.");
        }

        Long facilityId = request == null
                ? null : request.facilityId();

        if (facilityId == null
                && metadata != null
                && zoneId.equals(metadata.zoneId())) {
            facilityId = metadata.facilityId();
        }

        var target = metadataRepository.findTarget(
                loginId, zoneId, facilityId
        );

        if (!video.siteId().equals(target.siteId())) {
            throw badRequest("영상과 시설물은 같은 현장이어야 합니다.");
        }

        if (!repository.prepareRetry(videoId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "FAILED 상태의 영상만 재분석할 수 있습니다."
            );
        }

        metadataRepository.clearError(videoId);

        boolean queued = enqueue(new AnalysisTask(
                videoId,
                loginId,
                video.siteId(),
                target.facilityId(),
                target.zoneName(),
                video.objectKey()
        ));

        return new VideoDtos.UploadResponse(
                videoId,
                video.siteId(),
                queued ? "PENDING" : "FAILED"
        );
    }

    private VideoRepository.VideoRow findVideo(
            String loginId,
            Long videoId
    ) {
        var rows = repository.findVideos(
                loginId, null, null, videoId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "영상을 찾을 수 없거나 접근할 수 없습니다."
            );
        }

        return rows.getFirst();
    }

    private boolean enqueue(AnalysisTask task) {
        try {
            executor.execute(() -> analyze(task));
            return true;
        } catch (RejectedExecutionException exception) {
            metadataRepository.fail(
                    task.videoId(),
                    "분석 대기열이 가득 찼습니다. 잠시 후 재시도해주세요."
            );
            return false;
        }
    }

    private void analyze(AnalysisTask task) {
        try {
            if (!repository.startAnalysis(task.videoId())) {
                return;
            }

            var request = new AiRequests.AnalyzeVideo(
                    task.videoId(),
                    task.facilityId(),
                    resolveUrl(task.objectKey()),
                    Map.<String, Object>of(
                            "site_id", task.siteId().toString()
                    ),
                    Map.<String, Object>of(
                            "work_name", "현장 안전 점검",
                            "work_type", "시설물 안전 점검",
                            "location", Objects.requireNonNullElse(
                                    task.zoneName(), ""
                            )
                    )
            );

            aiService.analyzeVideo(request, task.loginId());
            repository.complete(task.videoId());
        } catch (Exception exception) {
            log.error(
                    "영상 분석 실패: videoId={}",
                    task.videoId(),
                    exception
            );

            String reason =
                    exception instanceof ResponseStatusException status
                            ? status.getReason()
                            : "AI 분석 처리에 실패했습니다.";

            try {
                metadataRepository.fail(
                        task.videoId(),
                        Objects.requireNonNullElse(
                                reason, "AI 분석 처리에 실패했습니다."
                        )
                );
            } catch (Exception updateException) {
                log.error("분석 실패 상태 기록 실패", updateException);
            }
        }
    }

    private VideoDtos.VideoItem toItem(
            VideoRepository.VideoRow row,
            VideoMetadataRepository.Metadata metadata
    ) {
        String status = Objects.requireNonNullElse(
                row.status(), "PENDING"
        );

        String analysisStatus = switch (status) {
            case "COMPLETED" -> "complete";
            case "FAILED" -> "failed";
            default -> "processing";
        };

        String fileName = metadata == null
                ? null : metadata.fileName();

        String area = metadata == null ? null : metadata.area();

        OffsetDateTime recordedAt = metadata == null
                ? null : metadata.recordedAt();

        return new VideoDtos.VideoItem(
                row.id(),
                row.siteId(),
                fileName == null ? "영상 " + row.id() : fileName,
                area == null ? "구역 정보 없음" : area,
                metadata == null ? null : metadata.zoneId(),
                metadata == null ? null : metadata.facilityId(),
                recordedAt == null
                        ? "미제공"
                        : DISPLAY_TIME.format(recordedAt),
                metadata == null
                        ? "UNKNOWN"
                        : Objects.requireNonNullElse(
                        metadata.recordedAtSource(), "UNKNOWN"
                ),
                row.uploadedAt(),
                parseDuration(row.totalTime()),
                status,
                analysisStatus,
                // 기존 프론트의 정적 썸네일 배열 기본 인덱스
                0,
                resolveUrl(row.thumbnailKey()),
                resolveUrl(row.objectKey()),
                metadata == null ? null : metadata.analysisError(),
                row.riskCount(),
                row.highCount(),
                row.unresolvedCount()
        );
    }

    private String resolveUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        if (value.startsWith("https://")
                || value.startsWith("http://")) {
            return value;
        }

        return storageService.createSignedDownloadUrl(value);
    }

    private String formatDuration(Integer seconds) {
        return seconds == null ? null : String.format(
                Locale.ROOT,
                "%02d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                seconds % 60
        );
    }

    private Integer parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            String[] parts = value.trim().split(":");
            if (parts.length != 3) return null;

            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);

            if (hours < 0 || minutes < 0 || minutes > 59
                    || seconds < 0 || seconds > 59) {
                return null;
            }

            long total = Math.addExact(
                    Math.addExact(
                            Math.multiplyExact(hours, 3600L),
                            minutes * 60
                    ),
                    seconds
            );

            return total > Integer.MAX_VALUE ? null : (int) total;
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, message
        );
    }
}