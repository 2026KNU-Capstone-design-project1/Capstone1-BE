package com.example.backend.domain.AI.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;

import java.util.Map;

public final class AiRequests {
    private AiRequests() {}

    public record AnalyzeVideo(
            @JsonProperty("video_id")
            @NotNull
            @Positive
            Long videoId,

            @JsonProperty("video_url")
            @NotBlank
            String videoUrl,

            @JsonProperty("site_info")
            @NotEmpty
            Map<String, Object> siteInfo,

            @JsonProperty("work_info")
            Map<String, Object> workInfo
    ) {}

    public record PreviewAssess(
            @JsonProperty("site_info")
            @NotEmpty
            Map<String, Object> siteInfo,

            @JsonProperty("work_info")
            Map<String, Object> workInfo,

            @NotNull
            JsonNode preprocessing
    ) {}
}