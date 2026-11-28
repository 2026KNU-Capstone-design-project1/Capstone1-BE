package com.example.backend.domain.video.controller;

import com.example.backend.domain.video.dto.VideoDtos;
import com.example.backend.domain.video.service.VideoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/video")
@Validated
public class VideoController {

    private final VideoService service;

    public VideoController(VideoService service) {
        this.service = service;
    }

    @GetMapping("/upload-options")
    public List<VideoDtos.ZoneOption> getUploadOptions(
            @AuthenticationPrincipal String loginId
    ) {
        return service.getUploadOptions(loginId);
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<VideoDtos.UploadResponse> upload(
            @AuthenticationPrincipal String loginId,

            @RequestParam(name = "zoneId", required = false)
            @Positive Long zoneId,

            @RequestParam(name = "areaId", required = false)
            String areaId,

            @RequestParam(name = "facilityId", required = false)
            @Positive Long facilityId,

            @RequestParam(name = "durationSeconds", required = false)
            @Positive Integer durationSeconds,

            @RequestParam(name = "recordedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime recordedAt,

            @RequestPart("file") MultipartFile file
    ) {
        Long resolvedZoneId = service.resolveZoneId(
                loginId, zoneId, areaId
        );

        var response = service.upload(
                loginId,
                resolvedZoneId,
                facilityId,
                durationSeconds,
                recordedAt,
                file
        );

        return ResponseEntity.accepted()
                .location(URI.create(
                        "/api/video/folders/" + response.videoId()
                ))
                .body(response);
    }

    @PostMapping(
            value = "/upload/batch",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<VideoDtos.BatchResponse> uploadBatch(
            @AuthenticationPrincipal String loginId,

            @RequestParam(name = "zoneId", required = false)
            @Positive Long zoneId,

            @RequestParam(name = "areaId", required = false)
            String areaId,

            @RequestParam(name = "facilityId", required = false)
            @Positive Long facilityId,

            @RequestPart("files") List<MultipartFile> files
    ) {
        Long resolvedZoneId = service.resolveZoneId(
                loginId, zoneId, areaId
        );

        var response = service.uploadBatch(
                loginId, resolvedZoneId, facilityId, files
        );

        boolean anyAccepted = response.items().stream()
                .anyMatch(VideoDtos.BatchItem::accepted);

        return ResponseEntity.status(
                anyAccepted
                        ? HttpStatus.ACCEPTED
                        : HttpStatus.BAD_REQUEST
        ).body(response);
    }

    @GetMapping
    public List<VideoDtos.FolderItem> getFolders(
            @AuthenticationPrincipal String loginId,
            @RequestParam(name = "siteId", required = false)
            @Positive Long siteId
    ) {
        return service.getFolders(loginId, siteId);
    }

    @GetMapping("/folders")
    public List<VideoDtos.VideoItem> getVideos(
            @AuthenticationPrincipal String loginId,

            @RequestParam(name = "siteId", required = false)
            @Positive Long siteId,

            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return service.getVideos(loginId, siteId, date);
    }

    @GetMapping({"/folders/{videoId}", "/{videoId}"})
    public VideoDtos.VideoItem getVideo(
            @AuthenticationPrincipal String loginId,
            @PathVariable("videoId") @Positive Long videoId
    ) {
        return service.getVideo(loginId, videoId);
    }

    @PostMapping("/{videoId}/analysis")
    public ResponseEntity<VideoDtos.UploadResponse> retry(
            @AuthenticationPrincipal String loginId,
            @PathVariable("videoId") @Positive Long videoId,
            @Valid @RequestBody(required = false)
            VideoDtos.RetryRequest request
    ) {
        return ResponseEntity.accepted()
                .body(service.retry(loginId, videoId, request));
    }
}