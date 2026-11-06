package com.example.backend.global.storage;

import com.example.backend.global.storage.dto.PresignedDownloadResponse;
import com.example.backend.global.storage.dto.PresignedUploadRequest;
import com.example.backend.global.storage.dto.PresignedUploadResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final S3StorageService storageService;

    public StorageController(S3StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(
            value = "/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<PresignedDownloadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("type") String type
    ) {
        PresignedDownloadResponse response = storageService.upload(
                file,
                StorageObjectType.from(type)
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/upload-urls")
    public ResponseEntity<PresignedUploadResponse> createUploadUrl(
            @Valid @RequestBody PresignedUploadRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storageService.createUploadUrl(request));
    }

    @GetMapping("/download-urls")
    public PresignedDownloadResponse createDownloadUrl(
            @RequestParam("key") String objectKey
    ) {
        return storageService.createDownloadUrl(objectKey);
    }

}
