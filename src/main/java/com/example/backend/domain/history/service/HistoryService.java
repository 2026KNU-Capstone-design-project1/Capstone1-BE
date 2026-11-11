package com.example.backend.domain.history.service;

import com.example.backend.domain.history.dto.HistoryResponse;
import com.example.backend.domain.history.repository.HistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HistoryService {

    private static final int LONG_TERM_DAYS = 5;
    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    private final HistoryRepository historyRepository;

    public HistoryService(HistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public HistoryResponse getHistory() {
        OffsetDateTime daysAgo = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(LONG_TERM_DAYS);

        Map<Long, List<HistoryResponse.HistoryEvent>> eventsByRiskId =
                historyRepository.findHistoryEvents()
                        .stream()
                        .collect(Collectors.groupingBy(
                                HistoryRepository.HistoryEventView::getRiskIssueId,
                                Collectors.mapping(
                                        this::toHistoryEvent,
                                        Collectors.toList()
                                )
                        ));

        List<HistoryResponse.RiskHistoryItem> histories = historyRepository
                .findHistoryItems()
                .stream()
                .map(item -> toHistoryItem(
                        item,
                        eventsByRiskId.getOrDefault(
                                item.getRiskId(),
                                List.of()
                        )
                ))
                .toList();

        return new HistoryResponse(
                historyRepository.countRepeatedDetectionRisks(),
                historyRepository.countLongTermUnresolvedRisks(daysAgo),
                historyRepository.countRedetectedAfterActionRisks(),
                null,
                histories
        );
    }

    private HistoryResponse.RiskHistoryItem toHistoryItem(
            HistoryRepository.RiskHistoryItemView item,
            List<HistoryResponse.HistoryEvent> events
    ) {
        return new HistoryResponse.RiskHistoryItem(
                item.getRiskId(),
                item.getRiskType(),
                item.getHazard(),
                item.getZoneName(),
                item.getFacilityName(),
                toLocalDateTime(item.getFirstDetectedAt()),
                toLocalDateTime(item.getLastDetectedAt()),
                item.getDetectionCount(),
                item.getStatus(),
                events
        );
    }

    private HistoryResponse.HistoryEvent toHistoryEvent(
            HistoryRepository.HistoryEventView event
    ) {
        return new HistoryResponse.HistoryEvent(
                event.getId(),
                event.getStatus(),
                event.getChangedAt(),
                event.getEventType(),
                event.getCycle()
        );
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.atZoneSameInstant(RESPONSE_ZONE).toLocalDateTime();
    }
}
