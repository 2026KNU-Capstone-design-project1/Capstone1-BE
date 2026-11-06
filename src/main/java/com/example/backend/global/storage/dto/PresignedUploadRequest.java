package com.example.backend.global.storage.dto;

import com.example.backend.global.storage.StorageObjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PresignedUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 255) String contentType,
        @Positive long fileSize,
        @NotNull StorageObjectType type
) { }
