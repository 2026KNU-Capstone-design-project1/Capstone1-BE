package com.example.backend.domain.dashboard.service;

import com.example.backend.domain.dashboard.dto.DashboardResponse;
import com.example.backend.domain.dashboard.dto.DashboardResponse.MapRiskItem;
import com.example.backend.domain.dashboard.dto.DashboardResponse.MetricItem;
import com.example.backend.domain.dashboard.dto.DashboardResponse.RiskItem;
import com.example.backend.domain.dashboard.dto.DashboardResponse.SiteItem;
import com.example.backend.domain.dashboard.dto.DashboardResponse.UserItem;
import com.example.backend.domain.dashboard.repository.DashboardRepository;
import com.example.backend.domain.dashboard.repository.DashboardRepository.RiskRow;
import com.example.backend.global.storage.S3StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int LONG_TERM_DAYS = 5;

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private static final DateTimeFormatter STATUS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM.dd")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final DashboardRepository dashboardRepository;
    private final S3StorageService storageService;

    public DashboardService(
            DashboardRepository dashboardRepository,
            S3StorageService storageService
    ) {
        this.dashboardRepository = dashboardRepository;
        this.storageService = storageService;
    }

    public DashboardResponse getDashboard(
            String loginId,
            Long requestedSiteId
    ) {
        DashboardRepository.DashboardUser user = dashboardRepository
                .findUser(loginId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 사용자를 찾을 수 없습니다."
                ));

        List<SiteItem> sites = dashboardRepository
                .findAccessibleSites(loginId);

        if (sites.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "연결된 현장이 없습니다."
            );
        }

        SiteItem site = selectSite(sites, requestedSiteId);
        List<RiskRow> rows = dashboardRepository.findActiveRisks(site.id());

        List<RiskItem> risks = rows.stream()
                .map(this::toRiskItem)
                .toList();

        RiskItem featuredRisk = risks.stream()
                .filter(risk -> "OPEN".equals(risk.status()))
                .findFirst()
                .orElse(risks.isEmpty() ? null : risks.getFirst());

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(LONG_TERM_DAYS);

        long highCount = rows.stream()
                .filter(risk -> "HIGH".equals(risk.riskLevel()))
                .count();

        long longTermCount = rows.stream()
                .filter(risk -> risk.detectedAt() != null)
                .filter(risk -> !risk.detectedAt().isAfter(cutoff))
                .count();

        BigDecimal averageDays = dashboardRepository
                .findAverageResolutionDays(site.id());

        List<MetricItem> metrics = List.of(
                new MetricItem(
                        "total",
                        "전체 미처리 위험",
                        risks.size(),
                        null,
                        "warning",
                        "neutral"
                ),
                new MetricItem(
                        "high",
                        "고위험 미처리 건수",
                        highCount,
                        null,
                        "flame",
                        "danger"
                ),
                new MetricItem(
                        "longTerm",
                        "장기 미처리 위험",
                        longTermCount,
                        null,
                        "document",
                        "neutral"
                ),
                new MetricItem(
                        "average",
                        "평균 해결 시간",
                        averageDays,
                        "일",
                        "clock",
                        "neutral"
                )
        );

        List<MapRiskItem> riskZones = rows.stream()
                .map(risk -> new MapRiskItem(
                        risk.id(),
                        risk.hazard(),
                        risk.riskLevel(),
                        risk.status(),
                        risk.facilityId(),
                        risk.facilityName(),
                        risk.engName(),
                        risk.zoneId(),
                        formatDateTime(risk.detectedAt())
                ))
                .toList();

        return new DashboardResponse(
                site,
                sites,
                new UserItem(user.name(), user.role()),
                metrics,
                featuredRisk,
                risks,
                riskZones
        );
    }

    private SiteItem selectSite(
            List<SiteItem> sites,
            Long requestedSiteId
    ) {
        if (requestedSiteId == null) {
            return sites.getFirst();
        }

        if (requestedSiteId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "현장 ID는 양수여야 합니다."
            );
        }

        return sites.stream()
                .filter(site -> requestedSiteId.equals(site.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "해당 현장에 접근할 수 없습니다."
                ));
    }

    private RiskItem toRiskItem(RiskRow risk) {
        return new RiskItem(
                risk.id(),
                resolveImageUrl(risk.imageUrl()),
                risk.zoneName(),
                risk.facilityName(),
                risk.hazard(),
                risk.riskType(),
                risk.riskLevel(),
                formatDateTime(risk.detectedAt()),
                risk.status(),
                formatStatusDate(risk.statusChangedAt()),
                risk.userId(),
                risk.managerName() == null
                        ? "미배정"
                        : risk.managerName()
        );
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        if (imageUrl.startsWith("https://")
                || imageUrl.startsWith("http://")) {
            return imageUrl;
        }

        return storageService.createSignedDownloadUrl(imageUrl);
    }

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? null : DATE_TIME_FORMAT.format(value);
    }

    private String formatStatusDate(OffsetDateTime value) {
        return value == null ? null : STATUS_DATE_FORMAT.format(value);
    }
}