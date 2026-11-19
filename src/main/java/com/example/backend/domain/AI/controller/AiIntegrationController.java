package com.example.backend.domain.AI.controller;

import com.example.backend.domain.AI.dto.AiRequests;
import com.example.backend.domain.AI.service.AiIntegrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/AI")
public class AiIntegrationController {

    private final AiIntegrationService aiIntegrationService;

    public AiIntegrationController(
            AiIntegrationService aiIntegrationService
    ) {
        this.aiIntegrationService = aiIntegrationService;
    }

    @PostMapping("/yolo")
    public Map<String, Object> analyzeVideo(
            @Valid @RequestBody AiRequests.AnalyzeVideo request
    ) {
        return aiIntegrationService.analyzeVideo(request);
    }
}