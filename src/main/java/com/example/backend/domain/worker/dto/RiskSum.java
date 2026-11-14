package com.example.backend.domain.worker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskSum {
    Long id;
    String hazard;
    String zoneName;
    String facilityName;
    String level;
}
