package com.example.backend.domain.history.controller;

import com.example.backend.domain.history.dto.HistoryResponse;
import com.example.backend.domain.history.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<HistoryResponse> history() {
        return ResponseEntity.ok(historyService.getHistory());
    }
}
