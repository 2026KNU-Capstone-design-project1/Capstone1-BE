package com.example.backend.global.storage;

import com.example.backend.global.storage.dto.PresignedDownloadResponse;
import com.example.backend.global.storage.dto.PresignedUploadRequest;
import com.example.backend.global.storage.dto.PresignedUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class S3StorageService {

    private static final DateTimeFormatter KEY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            S3Properties properties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    /**
     * 작은 파일을 백엔드가 직접 S3에 업로드한다.
     * 영상처럼 큰 파일은 createUploadUrl을 사용해 S3로 바로 업로드하는 편이 좋다.
     */
    public PresignedDownloadResponse upload(
            MultipartFile file,
            StorageObjectType type
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "업로드할 파일을 선택해주세요."
            );
        }

        String contentType = validateFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                type
        );
        String objectKey = createObjectKey(
                file.getOriginalFilename(),
                type
        );

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    putRequest,
                    RequestBody.fromInputStream(inputStream, file.getSize())
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "업로드 파일을 읽을 수 없습니다.",
                    exception
            );
        } catch (SdkException exception) {
            throw storageFailure("S3 파일 업로드에 실패했습니다.", exception);
        }

        return createDownloadResponse(
                objectKey,
                contentType,
                file.getSize()
        );
    }

    /**
     * 클라이언트가 HTTP PUT으로 직접 업로드할 수 있는 일회성 URL을 만든다.
     */
    public PresignedUploadResponse createUploadUrl(
            PresignedUploadRequest request
    ) {
        String contentType = validateFile(
                request.fileName(),
                request.contentType(),
                request.fileSize(),
                request.type()
        );
        String objectKey = createObjectKey(
                request.fileName(),
                request.type()
        );

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(request.fileSize())
                .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(
                                properties.presignedUrlExpiration()
                        )
                        .putObjectRequest(putRequest)
                        .build();

        try {
            PresignedPutObjectRequest presigned =
                    s3Presigner.presignPutObject(presignRequest);
            OffsetDateTime expiresAt = OffsetDateTime
                    .now(ZoneOffset.UTC)
                    .plus(properties.presignedUrlExpiration());

            return new PresignedUploadResponse(
                    objectKey,
                    presigned.url().toExternalForm(),
                    Map.of("Content-Type", contentType),
                    expiresAt
            );
        } catch (SdkException exception) {
            throw storageFailure(
                    "S3 업로드 URL 생성에 실패했습니다.",
                    exception
            );
        }
    }

    /**
     * 비공개 객체를 읽을 수 있는 짧은 만료 시간의 URL을 만든다.
     */
    public PresignedDownloadResponse createDownloadUrl(String objectKey) {
        String validatedKey = validateObjectKey(objectKey);

        try {
            HeadObjectResponse object = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(validatedKey)
                            .build()
            );

            return createDownloadResponse(
                    validatedKey,
                    object.contentType(),
                    object.contentLength() == null
                            ? 0L
                            : object.contentLength()
            );
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "S3 파일을 찾을 수 없습니다.",
                        exception
                );
            }
            throw storageFailure("S3 파일 조회에 실패했습니다.", exception);
        } catch (SdkException exception) {
            throw storageFailure("S3 파일 조회에 실패했습니다.", exception);
        }
    }

    /**
     * 도메인 응답에 포함할 presigned GET URL을 만든다.
     * URL 서명은 로컬 연산이므로 S3 HeadObject 요청을 발생시키지 않는다.
     */
    public String createSignedDownloadUrl(String objectKey) {
        return presignDownloadUrl(validateObjectKey(objectKey));
    }

    private PresignedDownloadResponse createDownloadResponse(
            String objectKey,
            String contentType,
            long fileSize
    ) {
        String downloadUrl = presignDownloadUrl(objectKey);
        OffsetDateTime expiresAt = OffsetDateTime
                .now(ZoneOffset.UTC)
                .plus(properties.presignedUrlExpiration());

        return new PresignedDownloadResponse(
                objectKey,
                downloadUrl,
                contentType,
                fileSize,
                expiresAt
        );
    }

    private String presignDownloadUrl(String objectKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(
                                properties.presignedUrlExpiration()
                        )
                        .getObjectRequest(getRequest)
                        .build();

        try {
            PresignedGetObjectRequest presigned =
                    s3Presigner.presignGetObject(presignRequest);
            return presigned.url().toExternalForm();
        } catch (SdkException exception) {
            throw storageFailure(
                    "S3 다운로드 URL 생성에 실패했습니다.",
                    exception
            );
        }
    }

    private String validateFile(
            String fileName,
            String contentType,
            long fileSize,
            StorageObjectType type
    ) {
        if (type == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "파일 유형(type)을 입력해주세요."
            );
        }
        if (fileName == null || fileName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "파일명을 입력해주세요."
            );
        }
        if (fileSize <= 0 || fileSize > type.maxSizeBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "파일 크기는 0보다 크고 "
                            + type.maxSizeBytes()
                            + "바이트 이하여야 합니다."
            );
        }

        String normalizedContentType = normalizeContentType(contentType);
        if (!type.supports(normalizedContentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    type + " 유형에 허용되지 않는 Content-Type입니다."
            );
        }
        return normalizedContentType;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Content-Type을 입력해주세요."
            );
        }

        return contentType.split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String createObjectKey(
            String fileName,
            StorageObjectType type
    ) {
        String datePath = OffsetDateTime.now(ZoneOffset.UTC)
                .format(KEY_DATE_FORMAT);

        return properties.prefix(type)
                + "/" + datePath
                + "/" + UUID.randomUUID()
                + safeExtension(fileName);
    }

    private String safeExtension(String fileName) {
        String baseName = fileName.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        int extensionIndex = baseName.lastIndexOf('.');

        if (extensionIndex <= 0 || extensionIndex == baseName.length() - 1) {
            return "";
        }

        String extension = baseName.substring(extensionIndex)
                .toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,10}")
                ? extension
                : "";
    }

    private String validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()
                || objectKey.length() > 1024
                || objectKey.startsWith("/")
                || objectKey.contains("\\")
                || objectKey.contains("../")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 S3 objectKey를 입력해주세요."
            );
        }

        String trimmed = objectKey.trim();
        boolean allowed = properties.allowedPrefixes().stream()
                .anyMatch(prefix -> trimmed.startsWith(prefix + "/"));

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "허용되지 않는 S3 경로입니다."
            );
        }
        return trimmed;
    }

    private ResponseStatusException storageFailure(
            String message,
            Exception cause
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                message,
                cause
        );
    }
}
