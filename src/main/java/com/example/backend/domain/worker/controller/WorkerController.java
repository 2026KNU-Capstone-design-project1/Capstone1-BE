package com.example.backend.domain.worker.controller;

import com.example.backend.domain.worker.dto.WorkerHome;
import com.example.backend.domain.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/worker")
public class WorkerController {

    private final WorkerService workerService;

    @GetMapping
    public ResponseEntity<WorkerHome> worker(@AuthenticationPrincipal String loginId) {
        return ResponseEntity.ok(workerService.getWorkerHome(loginId));
    }

}
