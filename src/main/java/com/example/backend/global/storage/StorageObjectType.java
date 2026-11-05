package com.example.backend.global.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

public enum StorageObjectType {
    DETECTION(20L * 1024 * 1024, "image/"),
    RISK_THUMBNAIL(20L * 1024 * 1024, "image/"),
    VIDEO(2L * 1024 * 1024 * 1024, "video/");

    private final long maxSizeBytes;
    private final String contentTypePrefix;

    StorageObjectType(long maxSizeBytes, String contentTypePrefix) {
        this.maxSizeBytes = maxSizeBytes;
        this.contentTypePrefix = contentTypePrefix;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }

    public boolean supports(String contentType) {
        return contentType.startsWith(contentTypePrefix);
    }

    @JsonCreator
    public static StorageObjectType from(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "파일 유형(type)을 입력해주세요."
            );
        }

        try {
            return valueOf(value.trim()
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지원하지 않는 파일 유형입니다: " + value,
                    exception
            );
        }
    }
}
