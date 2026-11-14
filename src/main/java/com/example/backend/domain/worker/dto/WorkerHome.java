package com.example.backend.domain.worker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerHome {
    int openedCount;
    int inProgressCount;
    int completedCount;
    RiskSum mostPriorRisk;
    List<RiskSum> risks;
    String userName;
}
