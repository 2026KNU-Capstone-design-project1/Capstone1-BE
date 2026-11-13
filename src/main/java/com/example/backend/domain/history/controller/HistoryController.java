package com.example.backend.domain.history.controller;

import com.example.backend.domain.history.dto.HistoryResponse;
import com.example.backend.domain.history.dto.HistoryAiAnalysisResponse;
import com.example.backend.domain.history.service.HistoryAiAnalysisService;
import com.example.backend.domain.history.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;
    private final HistoryAiAnalysisService historyAiAnalysisService;

    public HistoryController(
            HistoryService historyService,
            HistoryAiAnalysisService historyAiAnalysisService
    ) {
        this.historyService = historyService;
        this.historyAiAnalysisService = historyAiAnalysisService;
    }

    @GetMapping
    public ResponseEntity<HistoryResponse> history() {
        return ResponseEntity.ok(historyService.getHistory());
    }

    @PostMapping("/ai-analysis")
    public ResponseEntity<HistoryAiAnalysisResponse> analyzeHistory() {
        return ResponseEntity.ok(historyAiAnalysisService.analyze());
    }
}
