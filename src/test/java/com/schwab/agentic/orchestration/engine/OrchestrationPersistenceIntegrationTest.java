package com.schwab.agentic.orchestration.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.ExecutionEventType;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskState;
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
class OrchestrationPersistenceIntegrationTest {

    @Autowired
    private OrchestrationPersistenceService persistenceService;

    @Autowired
    private DagValidator dagValidator;

    @Autowired
    private ExecutionRunRepository runRepository;

    @Autowired
    private TaskExecutionRepository taskRepository;

    @Autowired
    private ExecutionEventRepository eventRepository;

    @Autowired
    private DecisionRecordRepository decisionRepository;

    @BeforeEach
    void clearDatabase() {
        decisionRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void persistsRunTasksStateEventsAndDecisionLineage() {
        UUID runId = persistenceService.createRun(
                "greenfield",
                "Build a URL shortener",
                dagValidator.validate(List.of(
                        new TaskDefinition("requirements", TaskKind.REQUIREMENTS, Set.of(), ImpactLevel.LOW),
                        new TaskDefinition(
                                "implementation", TaskKind.CODE, Set.of("requirements"), ImpactLevel.HIGH))));

        assertEquals(RunStatus.CREATED, runRepository.findById(runId).orElseThrow().getStatus());
        var implementation = taskRepository.findByRunIdAndTaskId(runId, "implementation").orElseThrow();
        assertEquals(TaskState.PENDING, implementation.getState());
        assertEquals(Set.of("requirements"), implementation.getDependencyIds());

        persistenceService.transitionRun(
                runId, RunStatus.RUNNING, ActorType.SYSTEM, "scheduler", "Execution started");
        persistenceService.transitionTask(
                runId, "implementation", TaskState.READY, ActorType.SYSTEM, "scheduler", "Dependency complete");
        persistenceService.transitionTask(
                runId, "implementation", TaskState.RUNNING, ActorType.SYSTEM, "scheduler", "Task selected");

        var rootDecision = persistenceService.recordDecision(
                runId,
                "implementation",
                null,
                DecisionKind.PLAN,
                ActorType.AGENT,
                "architect",
                "Functional requirements",
                "Use a service/repository boundary");
        var childDecision = persistenceService.recordDecision(
                runId,
                "implementation",
                rootDecision.getId(),
                DecisionKind.POLICY,
                ActorType.POLICY,
                "guardrails",
                "Task impact is high",
                "Approval is required before execution");

        assertEquals(rootDecision.getId(), decisionRepository.findById(childDecision.getId())
                .orElseThrow()
                .getParentDecisionId());
        List<ExecutionEventType> eventTypes = eventRepository
                .findAllByRunIdOrderByOccurredAtAscIdAsc(runId)
                .stream()
                .map(event -> event.getEventType())
                .toList();
        assertEquals(8, eventTypes.size());
        assertEquals(2, eventTypes.stream().filter(type -> type == ExecutionEventType.DECISION_RECORDED).count());
        assertNotNull(runRepository.findById(runId).orElseThrow().getUpdatedAt());
    }

    @Test
    void rejectsInvalidTransitionWithoutChangingPersistedStateOrHistory() {
        UUID runId = persistenceService.createRun(
                "greenfield",
                null,
                dagValidator.validate(List.of(
                        new TaskDefinition("requirements", TaskKind.REQUIREMENTS, Set.of(), ImpactLevel.LOW))));
        long eventsBefore = eventRepository.count();

        assertThrows(
                InvalidStateTransitionException.class,
                () -> persistenceService.transitionTask(
                        runId,
                        "requirements",
                        TaskState.SUCCEEDED,
                        ActorType.SYSTEM,
                        "scheduler",
                        "Invalid direct transition"));

        assertEquals(TaskState.PENDING, taskRepository.findByRunIdAndTaskId(runId, "requirements")
                .orElseThrow()
                .getState());
        assertEquals(eventsBefore, eventRepository.count());
    }
}
