package com.example.backend.global.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "cloud.aws.s3")
public record S3Properties(
        @NotBlank String bucket,
        @NotNull Duration presignedUrlExpiration,
        @NotNull @Valid PathProperties path
) {
    public S3Properties {
        if (presignedUrlExpiration != null
                && (presignedUrlExpiration.isZero()
                || presignedUrlExpiration.isNegative()
                || presignedUrlExpiration.compareTo(Duration.ofDays(7)) > 0)) {
            throw new IllegalArgumentException(
                    "S3 presigned URL 만료 시간은 0초보다 크고 7일 이하여야 합니다."
            );
        }
    }

    public String prefix(StorageObjectType type) {
        String configured = switch (type) {
            case DETECTION -> path.detection();
            case RISK_THUMBNAIL -> path.riskThumbnail();
            case VIDEO -> path.video();
        };

        return configured.replaceAll("^/+|/+$", "");
    }

    public List<String> allowedPrefixes() {
        return List.of(
                prefix(StorageObjectType.DETECTION),
                prefix(StorageObjectType.RISK_THUMBNAIL),
                prefix(StorageObjectType.VIDEO)
        );
    }

    public record PathProperties(
            @NotBlank String detection,
            @NotBlank String riskThumbnail,
            @NotBlank String video
    ) { }
}
