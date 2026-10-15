package com.schwab.agentic.metrics;

import com.schwab.agentic.orchestration.approval.ApprovalRequestRepository;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.ExecutionEventEntity;
import com.schwab.agentic.orchestration.domain.ExecutionEventType;
import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReliabilityMetricsService {

    private static final Set<RunStatus> TERMINAL_STATUSES =
            EnumSet.of(RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.ROLLED_BACK, RunStatus.CANCELLED);

    private final ExecutionRunRepository runRepository;
    private final ExecutionEventRepository eventRepository;
    private final DecisionRecordRepository decisionRepository;
    private final ApprovalRequestRepository approvalRepository;

    public ReliabilityMetricsService(
            ExecutionRunRepository runRepository,
            ExecutionEventRepository eventRepository,
            DecisionRecordRepository decisionRepository,
            ApprovalRequestRepository approvalRepository) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.decisionRepository = decisionRepository;
        this.approvalRepository = approvalRepository;
    }

    @Transactional(readOnly = true)
    public ReliabilityMetricsResponse calculate() {
        long totalRuns = runRepository.count();
        List<ExecutionRunEntity> completedRuns = runRepository.findAllByStatusIn(TERMINAL_STATUSES);
        long successfulRuns = completedRuns.stream()
                .filter(run -> run.getStatus() == RunStatus.SUCCEEDED)
                .count();
        long rollbackCount = completedRuns.stream()
                .filter(run -> run.getStatus() == RunStatus.ROLLED_BACK)
                .count();

        List<Double> endToEndLatencies = completedRuns.stream()
                .map(run -> millisecondsBetween(run.getCreatedAt(), run.getUpdatedAt()))
                .filter(value -> value >= 0)
                .toList();
        List<Double> recoveryTimes = recoveryTimes(completedRuns);
        List<Double> approvalWaits = approvalRepository.findAllByDecidedAtIsNotNull().stream()
                .map(approval -> millisecondsBetween(approval.getRequestedAt(), approval.getDecidedAt()))
                .filter(value -> value >= 0)
                .toList();
        long retryCount = decisionRepository.countByKind(DecisionKind.RETRY);
        long completedCount = completedRuns.size();

        return new ReliabilityMetricsResponse(
                totalRuns,
                completedCount,
                successfulRuns,
                completedCount - successfulRuns,
                ratio(successfulRuns, completedCount),
                retryCount,
                rollbackCount,
                ratio(rollbackCount, completedCount),
                recoveryTimes.size(),
                mean(recoveryTimes),
                percentile(endToEndLatencies, 0.50),
                percentile(endToEndLatencies, 0.95),
                approvalWaits.size(),
                mean(approvalWaits),
                percentile(approvalWaits, 0.95));
    }

    private List<Double> recoveryTimes(List<ExecutionRunEntity> completedRuns) {
        Map<UUID, Instant> failureStartedAt = new HashMap<>();
        List<ExecutionEventEntity> recoverySignals = new ArrayList<>();
        recoverySignals.addAll(eventRepository.findAllByEventTypeAndNewValueIn(
                ExecutionEventType.RUN_STATE_CHANGED, List.of("ROLLING_BACK", "FAILED")));
        recoverySignals.addAll(eventRepository.findAllByEventTypeAndNewValueIn(
                ExecutionEventType.TASK_STATE_CHANGED, List.of("RETRYING", "FAILED")));
        for (ExecutionEventEntity event : recoverySignals) {
            failureStartedAt.merge(event.getRunId(), event.getOccurredAt(), (existing, candidate) ->
                    existing.isBefore(candidate) ? existing : candidate);
        }
        return completedRuns.stream()
                .filter(run -> run.getStatus() == RunStatus.SUCCEEDED || run.getStatus() == RunStatus.ROLLED_BACK)
                .map(run -> {
                    Instant startedAt = failureStartedAt.get(run.getId());
                    return startedAt == null ? -1.0 : millisecondsBetween(startedAt, run.getUpdatedAt());
                })
                .filter(value -> value >= 0)
                .toList();
    }

    private double millisecondsBetween(Instant start, Instant end) {
        return Duration.between(start, end).toNanos() / 1_000_000.0;
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double percentile(List<Double> values, double quantile) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = Math.max(0, (int) Math.ceil(quantile * sorted.size()) - 1);
        return sorted.get(index);
    }
}
