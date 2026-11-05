package com.example.backend.global.storage.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PresignedUploadResponse(
        String objectKey,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        OffsetDateTime expiresAt
) { }
