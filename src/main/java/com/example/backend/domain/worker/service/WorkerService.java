package com.example.backend.domain.worker.service;

import com.example.backend.domain.risk.repository.RiskRepository;
import com.example.backend.domain.user.repository.UserRepository;
import com.example.backend.domain.worker.dto.WorkerHome;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final RiskRepository riskRepository;
    private final UserRepository userRepository;

    public WorkerHome getWorkerHome(@AuthenticationPrincipal String loginId) {

        WorkerHome home = new WorkerHome();
        home.setOpenedCount(riskRepository.countOpenedIssues(loginId));
        home.setInProgressCount(riskRepository.countInProgressIssues(loginId));
        home.setCompletedCount(riskRepository.countResolvedIssues(loginId));
        home.setMostPriorRisk(riskRepository.findMostPriorRisk(loginId));
        home.setRisks(riskRepository.find3Risks(loginId));
        home.setUserName(userRepository.findNameByLoginId(loginId));

        return home;
    }
}
