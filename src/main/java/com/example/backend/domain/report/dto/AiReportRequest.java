package com.example.backend.domain.report.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiReportRequest(

        @JsonProperty("report_id")
        Long reportId,

        @JsonProperty("scene_info")
        @NotNull
        @Valid
        SceneInfo sceneInfo,

        @JsonProperty("risk_assessment")
        @NotNull
        @Valid
        RiskAssessment riskAssessment,

        @JsonProperty("legal_information")
        @NotNull
        @Size(max = 100)
        List<@NotNull Object> legalInformation,

        @JsonProperty("improvement_measures")
        @NotNull
        @Size(max = 100)
        List<@NotNull Object> improvementMeasures,

        @JsonProperty("safety_measures")
        @NotNull
        @Size(max = 100)
        List<@NotNull Object> safetyMeasures
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SceneInfo(
            @NotBlank
            @Size(max = 2000)
            String location
    ) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskAssessment(
            @JsonProperty("expected_accidents")
            @NotNull
            @Size(max = 100)
            List<@NotNull Object> expectedAccidents
    ) {}
}