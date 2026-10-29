package com.example.backend.domain.risk.controller;

import com.example.backend.domain.risk.dto.RiskCreateRequest;
import com.example.backend.domain.risk.dto.RiskResponse;
import com.example.backend.domain.risk.dto.RiskStatusUpdateRequest;
import com.example.backend.domain.risk.service.RiskService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/risks")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService)
    {
        this.riskService = riskService;
    }

    @PostMapping
    public ResponseEntity<RiskResponse> create(
            @Valid @RequestBody RiskCreateRequest request
    ) {
        RiskResponse response = riskService.create(request);

        return ResponseEntity
                .created(URI.create("/api/risks/" + response.id()))
                .body(response);
    }

    @GetMapping
    public Page<RiskResponse> getRisks(
            @RequestParam(name = "facilityId", required = false)
            Long facilityId,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        return riskService.getRisks(facilityId, page, size);
    }

    @GetMapping("/{riskId}")
    public RiskResponse getRisk(
            @PathVariable("riskId") Long id
    ) {
        return riskService.getRisk(id);
    }

    @PatchMapping("/{riskId}/status")
    public RiskResponse updateStatus(
            @PathVariable("riskId") Long id,
            @Valid @RequestBody RiskStatusUpdateRequest request
    ) {
        return riskService.updateStatus(id, request);
    }
}