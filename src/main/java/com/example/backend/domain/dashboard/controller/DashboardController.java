package com.example.backend.domain.dashboard.controller;

import com.example.backend.domain.dashboard.dto.DashboardResponse;
import com.example.backend.domain.dashboard.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse getDashboard(
            @AuthenticationPrincipal String loginId,
            @RequestParam(name = "siteId", required = false) Long siteId
    ) {
        return dashboardService.getDashboard(loginId, siteId);
    }
}