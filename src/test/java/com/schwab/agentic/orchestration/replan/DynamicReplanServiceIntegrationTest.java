package com.schwab.agentic.orchestration.replan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schwab.agentic.orchestration.approval.ApprovalRequestRepository;
import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.dag.DagValidationException;
import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.ExecutionEventType;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.engine.ExecutionEngine;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DynamicReplanServiceIntegrationTest {

    @Autowired
    private DynamicReplanService replanService;

    @Autowired
    private ExecutionEngine executionEngine;

    @Autowired
    private DagValidator dagValidator;

    @Autowired
    private OrchestrationPersistenceService persistenceService;

    @Autowired
    private ApprovalService approvalService;

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

    @BeforeEach
    void clearDatabase() {
        approvalRepository.deleteAll();
        decisionRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void revisedUpstreamOutputInvalidatesOnlyItsDownstreamBranch() {
        List<TaskDefinition> definitions = List.of(
                task("requirements", TaskKind.REQUIREMENTS, Set.of()),
                task("implementation", TaskKind.CODE, Set.of("requirements")),
                task("tests", TaskKind.TEST, Set.of("implementation")),
                task("independent", TaskKind.REVIEW, Set.of()));
        UUID runId = createSucceededRun(definitions);
        var upstreamDecision = persistenceService.recordDecision(
                runId,
                "requirements",
                null,
                DecisionKind.PLAN,
                ActorType.AGENT,
                "requirements",
                "old input",
                "Original requirements");

        ReplanResult result = replanService.replan(
                runId,
                request(definitions, null, "requirements", "Revised requirements output"));

        assertEquals(Set.of("implementation", "tests"), result.invalidatedTaskIds());
        assertEquals(Set.of("requirements", "independent"), result.preservedTaskIds());
        assertEquals(TaskState.SUCCEEDED, task(runId, "requirements").getState());
        assertEquals("Revised requirements output", task(runId, "requirements").getResultSummary());
        assertEquals(TaskState.PENDING, task(runId, "implementation").getState());
        assertEquals(TaskState.PENDING, task(runId, "tests").getState());
        assertEquals(TaskState.SUCCEEDED, task(runId, "independent").getState());
        assertEquals(RunStatus.CREATED, runRepository.findById(runId).orElseThrow().getStatus());
        assertEquals(
                upstreamDecision.getId(),
                decisionRepository.findById(result.decisionRecordId()).orElseThrow().getParentDecisionId());
        assertTrue(eventRepository.findAllByRunIdOrderByOccurredAtAscIdAsc(runId).stream()
                .anyMatch(event -> event.getEventType() == ExecutionEventType.TASK_OUTPUT_REVISED));
    }

    @Test
    void changedTaskDefinitionInvalidatesOnlyItsTransitiveDependents() {
        List<TaskDefinition> original = List.of(
                task("root-a", TaskKind.REQUIREMENTS, Set.of()),
                task("change-me", TaskKind.CODE, Set.of("root-a")),
                task("downstream", TaskKind.TEST, Set.of("change-me")),
                task("root-b", TaskKind.SECURITY, Set.of()),
                task("unrelated", TaskKind.REVIEW, Set.of("root-b")));
        UUID runId = createSucceededRun(original);
        List<TaskDefinition> revised = List.of(
                task("root-a", TaskKind.REQUIREMENTS, Set.of()),
                task("change-me", TaskKind.REVIEW, Set.of("root-a")),
                task("downstream", TaskKind.TEST, Set.of("change-me")),
                task("root-b", TaskKind.SECURITY, Set.of()),
                task("unrelated", TaskKind.REVIEW, Set.of("root-b")));

        ReplanResult result = replanService.replan(runId, request(revised, null, null, null));

        assertEquals(Set.of("change-me", "downstream"), result.invalidatedTaskIds());
        assertEquals(Set.of("root-a", "root-b", "unrelated"), result.preservedTaskIds());
        assertEquals(TaskKind.REVIEW, task(runId, "change-me").getKind());
        assertEquals(TaskState.PENDING, task(runId, "downstream").getState());
    }

    @Test
    void inputChangeInvalidatesAllBranchesAndUpdatesRunInput() {
        List<TaskDefinition> definitions = List.of(
                task("root-a", TaskKind.REQUIREMENTS, Set.of()),
                task("child-a", TaskKind.CODE, Set.of("root-a")),
                task("root-b", TaskKind.SECURITY, Set.of()));
        UUID runId = createSucceededRun(definitions);

        ReplanResult result = replanService.replan(
                runId, request(definitions, "Updated user requirement", null, null));

        assertEquals(Set.of("root-a", "child-a", "root-b"), result.invalidatedTaskIds());
        assertTrue(result.preservedTaskIds().isEmpty());
        assertEquals("Updated user requirement", runRepository.findById(runId).orElseThrow().getInputSummary());
        assertTrue(eventRepository.findAllByRunIdOrderByOccurredAtAscIdAsc(runId).stream()
                .anyMatch(event -> event.getEventType() == ExecutionEventType.RUN_INPUT_REVISED));
    }

    @Test
    void additionsAndRemovalsArePersistedWithoutLosingRunHistory() {
        List<TaskDefinition> original = List.of(
                task("stable", TaskKind.REQUIREMENTS, Set.of()),
                task("retired", TaskKind.CUSTOM, Set.of()));
        UUID runId = createSucceededRun(original);
        List<TaskDefinition> revised = List.of(
                task("stable", TaskKind.REQUIREMENTS, Set.of()),
                task("added", TaskKind.REQUIREMENTS, Set.of()));

        ReplanResult result = replanService.replan(runId, request(revised, null, null, null));

        TaskExecutionEntity retired = task(runId, "retired");
        assertEquals(Set.of("added"), result.addedTaskIds());
        assertEquals(Set.of("retired"), result.removedTaskIds());
        assertTrue(retired.isRetired());
        assertEquals(TaskState.SKIPPED, retired.getState());
        assertEquals(TaskState.PENDING, task(runId, "added").getState());
        assertEquals(RunStatus.CREATED, runRepository.findById(runId).orElseThrow().getStatus());

        assertEquals(RunStatus.SUCCEEDED, executionEngine.execute(runId).status());
        assertTrue(task(runId, "retired").isRetired());
        assertEquals(TaskState.SUCCEEDED, task(runId, "added").getState());

        List<TaskDefinition> reintroduced = List.of(
                task("stable", TaskKind.REQUIREMENTS, Set.of()),
                task("added", TaskKind.REQUIREMENTS, Set.of()),
                task("retired", TaskKind.REQUIREMENTS, Set.of()));
        ReplanResult restoreResult = replanService.replan(runId, request(reintroduced, null, null, null));
        assertTrue(restoreResult.addedTaskIds().isEmpty());
        assertFalse(task(runId, "retired").isRetired());
        assertEquals(TaskState.PENDING, task(runId, "retired").getState());
        assertEquals(RunStatus.SUCCEEDED, executionEngine.execute(runId).status());
    }

    @Test
    void rejectsInvalidDagWithoutChangingPersistedPlan() {
        List<TaskDefinition> original = List.of(
                task("first", TaskKind.REQUIREMENTS, Set.of()),
                task("second", TaskKind.CODE, Set.of("first")));
        UUID runId = createSucceededRun(original);
        List<TaskDefinition> cyclic = List.of(
                task("first", TaskKind.REQUIREMENTS, Set.of("second")),
                task("second", TaskKind.CODE, Set.of("first")));

        assertThrows(
                DagValidationException.class,
                () -> replanService.replan(runId, request(cyclic, "Ignored", null, null)));

        assertEquals(RunStatus.SUCCEEDED, runRepository.findById(runId).orElseThrow().getStatus());
        assertEquals(TaskState.SUCCEEDED, task(runId, "first").getState());
        assertEquals(Set.of("first"), task(runId, "second").getDependencyIds());
    }

    @Test
    void blocksReplanningDuringActiveExecutionAndPendingApproval() {
        List<TaskDefinition> definitions = List.of(
                task("work", TaskKind.REQUIREMENTS, Set.of()));
        UUID runId = persistenceService.createRun(
                "replan-test", "active work", dagValidator.validate(definitions));
        persistenceService.transitionRun(runId, RunStatus.RUNNING, ActorType.SYSTEM, "test", "Start run");

        assertThrows(
                IllegalStateException.class,
                () -> replanService.replan(runId, request(definitions, "Changed", null, null)));

        UUID approvalRunId = persistenceService.createRun(
                "replan-test",
                "pending approval",
                dagValidator.validate(List.of(task("high-impact", TaskKind.CODE, Set.of(), ImpactLevel.HIGH))));
        persistenceService.transitionRun(
                approvalRunId, RunStatus.RUNNING, ActorType.SYSTEM, "test", "Start approval test");
        approvalService.requestApproval(approvalRunId, "high-impact", "Approval required");

        assertThrows(
                IllegalStateException.class,
                () -> replanService.replan(
                        approvalRunId,
                        request(List.of(task("high-impact", TaskKind.CODE, Set.of(), ImpactLevel.HIGH)), "Changed", null, null)));
    }

    private UUID createSucceededRun(List<TaskDefinition> definitions) {
        var dag = dagValidator.validate(definitions);
        UUID runId = persistenceService.createRun("replan-test", "Initial requirements", dag);
        persistenceService.transitionRun(runId, RunStatus.RUNNING, ActorType.SYSTEM, "test", "Start baseline");
        for (String taskId : dag.topologicalOrder()) {
            persistenceService.transitionTask(
                    runId, taskId, TaskState.READY, ActorType.SYSTEM, "test", "Dependencies complete");
            persistenceService.transitionTask(
                    runId, taskId, TaskState.RUNNING, ActorType.SYSTEM, "test", "Task started");
            persistenceService.transitionTask(
                    runId,
                    taskId,
                    TaskState.SUCCEEDED,
                    ActorType.AGENT,
                    "test-agent",
                    "Task completed",
                    "Original output for " + taskId);
        }
        persistenceService.transitionRun(runId, RunStatus.SUCCEEDED, ActorType.SYSTEM, "test", "Baseline complete");
        return runId;
    }

    private ReplanRequest request(
            List<TaskDefinition> definitions,
            String updatedInputSummary,
            String changedTaskId,
            String updatedTaskOutput) {
        return new ReplanRequest(
                updatedInputSummary,
                definitions,
                changedTaskId,
                updatedTaskOutput,
                "assessment-reviewer",
                "Revised requirements or upstream output need a dependency-aware plan update");
    }

    private TaskDefinition task(String id, TaskKind kind, Set<String> dependencies) {
        return task(id, kind, dependencies, ImpactLevel.LOW);
    }

    private TaskDefinition task(String id, TaskKind kind, Set<String> dependencies, ImpactLevel impact) {
        return new TaskDefinition(id, kind, dependencies, impact);
    }

    private TaskExecutionEntity task(UUID runId, String taskId) {
        return taskRepository.findByRunIdAndTaskId(runId, taskId).orElseThrow();
    }
}
