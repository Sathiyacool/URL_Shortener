package com.schwab.agentic.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schwab.agentic.orchestration.api.OrchestrationRunService;
import com.schwab.agentic.orchestration.approval.ApprovalDecision;
import com.schwab.agentic.orchestration.approval.ApprovalRequestRepository;
import com.schwab.agentic.orchestration.approval.ApprovalStatus;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.engine.ExecutionMode;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import com.schwab.agentic.scenarios.ScenarioLifecycleService.LifecycleOptions;
import com.schwab.agentic.shortener.repository.ClickEventRepository;
import com.schwab.agentic.shortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScenarioLifecycleIntegrationTest {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private ScenarioLifecycleService lifecycleService;

    @Autowired
    private OrchestrationRunService runService;

    @Autowired
    private ApprovalRequestRepository approvalRepository;

    @Autowired
    private DecisionRecordRepository decisionRepository;

    @Autowired
    private ExecutionEventRepository eventRepository;

    @Autowired
    private TaskExecutionRepository taskRepository;

    @Autowired
    private ExecutionRunRepository runRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @BeforeEach
    void clearState() {
        approvalRepository.deleteAll();
        decisionRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
        clickEventRepository.deleteAll();
        shortUrlRepository.deleteAll();
    }

    private LifecycleOptions options(ApprovalDecision decision, boolean safeStop) {
        return new LifecycleOptions(decision, "lifecycle-reviewer", "Lifecycle integration test decision", safeStop);
    }

    @Test
    void brownfieldApprovalResumesAndSucceedsWithAttributedDecision() {
        var initial = scenarioService.run("brownfield", ExecutionMode.SEQUENTIAL);
        assertEquals(RunStatus.AWAITING_APPROVAL, initial.status());

        var outcome = lifecycleService.drive(
                initial, ExecutionMode.SEQUENTIAL, options(ApprovalDecision.APPROVE, false));

        assertEquals(RunStatus.SUCCEEDED, outcome.finalStatus());
        assertTrue(outcome.steps().stream().anyMatch(step -> step.contains("APPROVED by lifecycle-reviewer")));
        var approvals = approvalRepository.findAll();
        assertEquals(1, approvals.size());
        assertEquals(ApprovalStatus.APPROVED, approvals.getFirst().getStatus());
        assertEquals("lifecycle-reviewer", approvals.getFirst().getApprover());
        assertTrue(runService.get(initial.runId()).tasks().stream()
                .allMatch(task -> task.state() == TaskState.SUCCEEDED));
        assertTrue(runService.verifyAudit(initial.runId()).valid());
    }

    @Test
    void ambiguousApprovalResumesAndSucceeds() {
        var initial = scenarioService.run("ambiguous", ExecutionMode.PARALLEL);
        assertEquals(RunStatus.AWAITING_APPROVAL, initial.status());

        var outcome = lifecycleService.drive(
                initial, ExecutionMode.PARALLEL, options(ApprovalDecision.APPROVE, false));

        assertEquals(RunStatus.SUCCEEDED, outcome.finalStatus());
        assertTrue(runService.get(initial.runId()).tasks().stream()
                .allMatch(task -> task.state() == TaskState.SUCCEEDED));
    }

    @Test
    void rejectionCancelsRunAndNeverDispatchesTheGatedTask() {
        var initial = scenarioService.run("brownfield", ExecutionMode.SEQUENTIAL);

        var outcome = lifecycleService.drive(
                initial, ExecutionMode.SEQUENTIAL, options(ApprovalDecision.REJECT, false));

        assertEquals(RunStatus.CANCELLED, outcome.finalStatus());
        var tasks = runService.get(initial.runId()).tasks();
        assertTrue(tasks.stream()
                .filter(task -> task.taskId().equals("implementation"))
                .noneMatch(task -> task.state() == TaskState.SUCCEEDED));
        assertEquals(ApprovalStatus.REJECTED, approvalRepository.findAll().getFirst().getStatus());
        assertTrue(runService.verifyAudit(initial.runId()).valid());
    }

    @Test
    void safeStopAfterApprovalHaltsBeforeDispatchAndThenResumesToSuccess() {
        var initial = scenarioService.run("brownfield", ExecutionMode.SEQUENTIAL);

        var outcome = lifecycleService.drive(
                initial, ExecutionMode.SEQUENTIAL, options(ApprovalDecision.APPROVE, true));

        assertTrue(outcome.steps().stream().anyMatch(step -> step.contains("execute status=SAFE_STOPPED")));
        assertEquals(RunStatus.SUCCEEDED, outcome.finalStatus());
    }

    @Test
    void lifecycleOptionsRequireExplicitDecisionApproverAndRationale() {
        assertThrows(IllegalArgumentException.class, () -> new LifecycleOptions(null, "a", "r", false));
        assertThrows(IllegalArgumentException.class,
                () -> new LifecycleOptions(ApprovalDecision.APPROVE, " ", "r", false));
        assertThrows(IllegalArgumentException.class,
                () -> new LifecycleOptions(ApprovalDecision.APPROVE, "a", "", false));
    }
}
