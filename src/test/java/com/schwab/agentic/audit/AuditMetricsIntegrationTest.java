package com.schwab.agentic.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schwab.agentic.metrics.ReliabilityMetricsService;
import com.schwab.agentic.orchestration.approval.ApprovalRequestEntity;
import com.schwab.agentic.orchestration.approval.ApprovalRequestRepository;
import com.schwab.agentic.orchestration.approval.ApprovalStatus;
import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.ExecutionEventEntity;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuditMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditIntegrityService auditIntegrityService;

    @Autowired
    private ReliabilityMetricsService metricsService;

    @Autowired
    private OrchestrationPersistenceService persistenceService;

    @Autowired
    private DagValidator dagValidator;

    @Autowired
    private ExecutionEventRepository eventRepository;

    @Autowired
    private DecisionRecordRepository decisionRepository;

    @Autowired
    private ApprovalRequestRepository approvalRepository;

    @Autowired
    private TaskExecutionRepository taskRepository;

    @Autowired
    private ExecutionRunRepository runRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearState() {
        approvalRepository.deleteAll();
        decisionRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void verifiesHashChainAndDetectsEventContentTamperingThroughApi() throws Exception {
        UUID runId = createRun("audit-integrity", task("task", ImpactLevel.LOW));

        var valid = auditIntegrityService.verifyRun(runId);
        assertTrue(valid.valid(), valid.toString());
        assertTrue(valid.eventCount() >= 2);
        assertEquals(1L, eventRepository.findAllByRunIdOrderBySequenceNumberAsc(runId)
                .getFirst()
                .getSequenceNumber());

        mockMvc.perform(get("/api/runs/{runId}/audit/verify", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.eventCount").value(valid.eventCount()));

        ExecutionEventEntity firstEvent =
                eventRepository.findAllByRunIdOrderBySequenceNumberAsc(runId).getFirst();
        jdbcTemplate.update(
                "UPDATE orchestration_events SET details = ? WHERE id = ?",
                "tampered details",
                firstEvent.getId());

        var tampered = auditIntegrityService.verifyRun(runId);
        assertFalse(tampered.valid());
        assertEquals(1L, tampered.firstInvalidSequence());
        mockMvc.perform(get("/api/runs/{runId}/audit/verify", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.firstInvalidSequence").value(1));
    }

    @Test
    void serializesConcurrentAuditEventsAndDetectsTruncatedTail() throws Exception {
        List<TaskDefinition> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            tasks.add(task("task-" + index, ImpactLevel.LOW));
        }
        UUID runId = persistenceService.createRun("concurrent-audit", "Concurrent events", dagValidator.validate(tasks));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Callable<Void>> writes = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                int taskIndex = index;
                writes.add(() -> {
                    if (!start.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Audit writers did not receive a start signal");
                    }
                    persistenceService.recordDecision(
                            runId,
                            "task-" + taskIndex,
                            null,
                            DecisionKind.PLAN,
                            ActorType.AGENT,
                            "concurrent-test",
                            "input",
                            "decision-" + taskIndex);
                    return null;
                });
            }
            List<java.util.concurrent.Future<Void>> futures = new ArrayList<>();
            for (var write : writes) {
                futures.add(executor.submit(write));
            }
            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        var concurrentVerification = auditIntegrityService.verifyRun(runId);
        assertTrue(concurrentVerification.valid(), concurrentVerification.toString());

        ExecutionEventEntity lastEvent =
                eventRepository.findFirstByRunIdOrderBySequenceNumberDesc(runId).orElseThrow();
        eventRepository.delete(lastEvent);
        assertFalse(auditIntegrityService.verifyRun(runId).valid());
    }

    @Test
    void calculatesReliabilityMetricsAndExposesThemThroughApi() throws Exception {
        UUID successfulRun = createRun("metrics-success", task("work", ImpactLevel.HIGH));
        persistenceService.transitionRun(successfulRun, RunStatus.RUNNING, ActorType.SYSTEM, "test", "Start");
        persistenceService.transitionRun(successfulRun, RunStatus.SUCCEEDED, ActorType.SYSTEM, "test", "Done");

        UUID rolledBackRun = createRun("metrics-rollback", task("rollback-work", ImpactLevel.LOW));
        persistenceService.transitionRun(rolledBackRun, RunStatus.RUNNING, ActorType.SYSTEM, "test", "Start");
        persistenceService.recordDecision(
                rolledBackRun,
                "rollback-work",
                null,
                DecisionKind.RETRY,
                ActorType.SYSTEM,
                "retry-policy",
                "Attempt",
                "Retry after transient failure");
        persistenceService.transitionRun(
                rolledBackRun, RunStatus.ROLLING_BACK, ActorType.SYSTEM, "test", "Compensating");
        persistenceService.transitionRun(
                rolledBackRun, RunStatus.ROLLED_BACK, ActorType.SYSTEM, "test", "Compensation complete");

        UUID cancelledRun = createRun("metrics-cancelled", task("cancelled-work", ImpactLevel.LOW));
        persistenceService.transitionRun(cancelledRun, RunStatus.RUNNING, ActorType.SYSTEM, "test", "Start");
        persistenceService.transitionRun(cancelledRun, RunStatus.CANCELLED, ActorType.SYSTEM, "test", "Cancelled");

        Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");
        ApprovalRequestEntity approval = new ApprovalRequestEntity(
                UUID.randomUUID(),
                successfulRun,
                "work",
                ImpactLevel.HIGH,
                "Review the high-impact task",
                UUID.randomUUID(),
                requestedAt);
        approval.decide(
                ApprovalStatus.APPROVED,
                "reviewer",
                "Approved",
                requestedAt.plusMillis(2_500));
        approvalRepository.save(approval);

        var metrics = metricsService.calculate();
        assertEquals(3, metrics.totalRuns());
        assertEquals(3, metrics.completedRuns());
        assertEquals(1, metrics.successfulRuns());
        assertEquals(2, metrics.failedRuns());
        assertEquals(1.0 / 3.0, metrics.successRate(), 0.00001);
        assertEquals(1, metrics.retryCount());
        assertEquals(1, metrics.rollbackCount());
        assertEquals(1.0 / 3.0, metrics.rollbackFrequency(), 0.00001);
        assertEquals(1, metrics.mttrSampleCount());
        assertEquals(1, metrics.approvalDecisionCount());
        assertEquals(2_500, metrics.meanApprovalWaitMs(), 0.001);
        assertEquals(2_500, metrics.p95ApprovalWaitMs(), 0.001);
        Instant recoveryStartedAt = eventRepository
                .findAllByRunIdOrderBySequenceNumberAsc(rolledBackRun)
                .stream()
                .filter(event -> event.getNewValue().equals(RunStatus.ROLLING_BACK.name()))
                .findFirst()
                .orElseThrow()
                .getOccurredAt();
        Instant recoveredAt = runRepository.findById(rolledBackRun).orElseThrow().getUpdatedAt();
        assertEquals(
                Duration.between(recoveryStartedAt, recoveredAt).toNanos() / 1_000_000.0,
                metrics.meanTimeToRecoveryMs(),
                0.001);
        assertTrue(metrics.p95EndToEndLatencyMs() >= metrics.p50EndToEndLatencyMs());

        mockMvc.perform(get("/api/metrics/reliability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedRuns").value(3))
                .andExpect(jsonPath("$.retryCount").value(1))
                .andExpect(jsonPath("$.rollbackCount").value(1))
                .andExpect(jsonPath("$.approvalDecisionCount").value(1))
                .andExpect(jsonPath("$.p95EndToEndLatencyMs").isNumber());
    }

    private UUID createRun(String scenarioId, TaskDefinition task) {
        return persistenceService.createRun(
                scenarioId,
                "Metrics and audit integration test",
                dagValidator.validate(List.of(task)));
    }

    private TaskDefinition task(String id, ImpactLevel impactLevel) {
        return new TaskDefinition(id, TaskKind.CODE, Set.of(), impactLevel);
    }
}
