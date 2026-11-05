package com.example.backend.global.storage.dto;

import java.time.OffsetDateTime;

public record PresignedDownloadResponse(
        String objectKey,
        String downloadUrl,
        String contentType,
        long fileSize,
        OffsetDateTime expiresAt
) { }
