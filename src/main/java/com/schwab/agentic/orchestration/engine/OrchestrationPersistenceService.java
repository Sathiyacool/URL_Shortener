package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.audit.AuditHashService;
import com.schwab.agentic.orchestration.dag.TaskDag;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.DecisionRecordEntity;
import com.schwab.agentic.orchestration.domain.ExecutionEventEntity;
import com.schwab.agentic.orchestration.domain.ExecutionEventType;
import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrchestrationPersistenceService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(OrchestrationPersistenceService.class);
    private static final int MAX_SUMMARY_LENGTH = 4000;
    private static final int MAX_ACTOR_REFERENCE_LENGTH = 128;

    private final ExecutionRunRepository runRepository;
    private final TaskExecutionRepository taskRepository;
    private final ExecutionEventRepository eventRepository;
    private final DecisionRecordRepository decisionRepository;
    private final TaskStateMachine taskStateMachine;
    private final RunStateMachine runStateMachine;
    private final Clock clock;
    private final AuditHashService auditHashService;

    public OrchestrationPersistenceService(
            ExecutionRunRepository runRepository,
            TaskExecutionRepository taskRepository,
            ExecutionEventRepository eventRepository,
            DecisionRecordRepository decisionRepository,
            TaskStateMachine taskStateMachine,
            RunStateMachine runStateMachine,
            Clock clock,
            AuditHashService auditHashService) {
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.decisionRepository = decisionRepository;
        this.taskStateMachine = taskStateMachine;
        this.runStateMachine = runStateMachine;
        this.clock = clock;
        this.auditHashService = auditHashService;
    }

    @Transactional
    public UUID createRun(String scenarioId, String inputSummary, TaskDag dag) {
        requireText(scenarioId, "Scenario ID", 64);
        requireOptionalText(inputSummary, "Input summary", MAX_SUMMARY_LENGTH);
        if (dag == null) {
            throw new IllegalArgumentException("A validated task DAG is required");
        }

        Instant now = clock.instant();
        UUID runId = UUID.randomUUID();
        ExecutionRunEntity run = runRepository.save(new ExecutionRunEntity(runId, scenarioId, inputSummary, now));
        appendEvent(
                runId,
                null,
                ExecutionEventType.RUN_CREATED,
                ActorType.SYSTEM,
                "orchestrator",
                null,
                run.getStatus().name(),
                inputSummary,
                now);

        for (String taskId : dag.topologicalOrder()) {
            TaskDefinition definition = dag.tasksById().get(taskId);
            taskRepository.save(new TaskExecutionEntity(
                    UUID.randomUUID(),
                    runId,
                    definition.taskId(),
                    definition.kind(),
                    definition.impactLevel(),
                    definition.dependencyIds()));
            appendEvent(
                    runId,
                    taskId,
                    ExecutionEventType.TASK_CREATED,
                    ActorType.SYSTEM,
                    "orchestrator",
                    null,
                    TaskState.PENDING.name(),
                    "dependencies=" + String.join(",", definition.dependencyIds()),
                    now);
        }
        log.info("Run created runId={} scenario={} tasks={}", runId, scenarioId, dag.topologicalOrder().size());
        return runId;
    }

    @Transactional
    public RunStatus transitionRun(
            UUID runId, RunStatus target, ActorType actorType, String actorReference, String details) {
        validateEventInput(actorType, actorReference, details);
        ExecutionRunEntity run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId));
        RunStatus previous = run.getStatus();
        runStateMachine.validate(previous, target);

        Instant now = clock.instant();
        run.transitionTo(target, now);
        runRepository.save(run);
        appendEvent(
                runId,
                null,
                ExecutionEventType.RUN_STATE_CHANGED,
                actorType,
                actorReference,
                previous.name(),
                target.name(),
                details,
                now);
        log.info("Run state runId={} {} -> {} actor={}/{} details={}",
                runId, previous, target, actorType, actorReference, details);
        return target;
    }

    @Transactional
    public TaskState transitionTask(
            UUID runId,
            String taskId,
            TaskState target,
            ActorType actorType,
            String actorReference,
            String details) {
        return transitionTask(runId, taskId, target, actorType, actorReference, details, null);
    }

    @Transactional
    public TaskState transitionTask(
            UUID runId,
            String taskId,
            TaskState target,
            ActorType actorType,
            String actorReference,
            String details,
            String resultSummary) {
        requireText(taskId, "Task ID", 100);
        validateEventInput(actorType, actorReference, details);
        requireOptionalText(resultSummary, "Task result", MAX_SUMMARY_LENGTH);
        TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, taskId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                        "Task not found in run " + runId + ": " + taskId));
        TaskState previous = task.getState();
        taskStateMachine.validate(previous, target);

        if (target == TaskState.INVALIDATED) {
            task.invalidateForReplan();
        } else {
            task.transitionTo(target);
            if (previous == TaskState.INVALIDATED && target == TaskState.PENDING) {
                task.resetForReplan();
            }
        }
        if (target == TaskState.RUNNING) {
            task.incrementAttemptCount();
        } else if (target == TaskState.SUCCEEDED) {
            task.setResultSummary(resultSummary);
        } else if (target == TaskState.FAILED) {
            task.setFailureSummary(details);
        }
        taskRepository.save(task);
        appendEvent(
                runId,
                taskId,
                ExecutionEventType.TASK_STATE_CHANGED,
                actorType,
                actorReference,
                previous.name(),
                target.name(),
                details,
                clock.instant());
        if (target == TaskState.FAILED || target == TaskState.RETRYING) {
            log.warn("Task state runId={} task={} {} -> {} actor={}/{} details={}",
                    runId, taskId, previous, target, actorType, actorReference, details);
        } else {
            log.info("Task state runId={} task={} {} -> {} actor={}/{}",
                    runId, taskId, previous, target, actorType, actorReference);
        }
        return target;
    }

    @Transactional
    public TaskExecutionEntity addTask(UUID runId, TaskDefinition definition) {
        if (!runRepository.existsById(runId)) {
            throw new OrchestrationRecordNotFoundException("Run not found: " + runId);
        }
        if (definition == null || taskRepository.existsByRunIdAndTaskId(runId, definition.taskId())) {
            throw new IllegalArgumentException("A new task definition with a unique task ID is required");
        }
        TaskExecutionEntity task = taskRepository.save(new TaskExecutionEntity(
                UUID.randomUUID(),
                runId,
                definition.taskId(),
                definition.kind(),
                definition.impactLevel(),
                definition.dependencyIds()));
        appendEvent(
                runId,
                definition.taskId(),
                ExecutionEventType.TASK_CREATED,
                ActorType.POLICY,
                "dynamic-replanner",
                null,
                TaskState.PENDING.name(),
                "Added during DAG replan; dependencies=" + String.join(",", definition.dependencyIds()),
                clock.instant());
        return task;
    }

    @Transactional
    public void updateTaskDefinition(UUID runId, String taskId, TaskDefinition definition) {
        if (definition == null || !taskId.equals(definition.taskId())) {
            throw new IllegalArgumentException("Updated task definition must retain the same task ID");
        }
        TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, taskId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                        "Task not found in run " + runId + ": " + taskId));
        task.updateDefinition(definition);
        taskRepository.save(task);
    }

    @Transactional
    public void retireTask(UUID runId, String taskId) {
        TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, taskId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                        "Task not found in run " + runId + ": " + taskId));
        task.retire();
        taskRepository.save(task);
    }

    @Transactional
    public void updateRunInputSummary(UUID runId, String inputSummary, String actorReference) {
        requireOptionalText(inputSummary, "Input summary", MAX_SUMMARY_LENGTH);
        ExecutionRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId));
        String previousSummary = run.getInputSummary();
        run.updateInputSummary(inputSummary, clock.instant());
        runRepository.save(run);
        appendEvent(
                runId,
                null,
                ExecutionEventType.RUN_INPUT_REVISED,
                ActorType.HUMAN,
                actorReference,
                hashForEvent(previousSummary),
                hashForEvent(inputSummary),
                "Run input summary revised during dynamic replan",
                clock.instant());
    }

    @Transactional
    public void reviseTaskOutput(UUID runId, String taskId, String resultSummary, String actorReference) {
        requireOptionalText(resultSummary, "Task result", MAX_SUMMARY_LENGTH);
        TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, taskId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                        "Task not found in run " + runId + ": " + taskId));
        if (task.getState() != TaskState.SUCCEEDED) {
            throw new IllegalArgumentException("Only a succeeded task output can be revised");
        }
        String previousSummary = task.getResultSummary();
        task.setResultSummary(resultSummary);
        taskRepository.save(task);
        appendEvent(
                runId,
                taskId,
                ExecutionEventType.TASK_OUTPUT_REVISED,
                ActorType.HUMAN,
                actorReference,
                hashForEvent(previousSummary),
                hashForEvent(resultSummary),
                "Upstream task output revised; dependent tasks will be invalidated",
                clock.instant());
    }

    @Transactional
    public void recordDagReplanned(UUID runId, String actorReference, String details) {
        validateEventInput(ActorType.HUMAN, actorReference, details);
        appendEvent(
                runId,
                null,
                ExecutionEventType.DAG_REPLANNED,
                ActorType.HUMAN,
                actorReference,
                null,
                null,
                details,
                clock.instant());
    }

    @Transactional
    public DecisionRecordEntity recordDecision(
            UUID runId,
            String taskId,
            UUID parentDecisionId,
            DecisionKind kind,
            ActorType actorType,
            String actorReference,
            String inputSummary,
            String rationale) {
        if (!runRepository.existsById(runId)) {
            throw new OrchestrationRecordNotFoundException("Run not found: " + runId);
        }
        if (taskId != null && !taskRepository.existsByRunIdAndTaskId(runId, taskId)) {
            throw new OrchestrationRecordNotFoundException("Task not found in run " + runId + ": " + taskId);
        }
        if (parentDecisionId != null) {
            DecisionRecordEntity parent = decisionRepository.findById(parentDecisionId)
                    .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                            "Parent decision not found: " + parentDecisionId));
            if (!parent.getRunId().equals(runId)) {
                throw new IllegalArgumentException("Parent decision must belong to the same run");
            }
        }
        if (kind == null || actorType == null) {
            throw new IllegalArgumentException("Decision kind and actor type are required");
        }
        requireText(actorReference, "Actor reference", MAX_ACTOR_REFERENCE_LENGTH);
        requireOptionalText(inputSummary, "Input summary", MAX_SUMMARY_LENGTH);
        requireText(rationale, "Decision rationale", MAX_SUMMARY_LENGTH);

        Instant now = clock.instant();
        UUID decisionId = UUID.randomUUID();
        DecisionRecordEntity decision = decisionRepository.save(new DecisionRecordEntity(
                decisionId,
                runId,
                taskId,
                parentDecisionId,
                kind,
                actorType,
                actorReference,
                inputSummary,
                rationale,
                now));
        appendEvent(
                runId,
                taskId,
                ExecutionEventType.DECISION_RECORDED,
                actorType,
                actorReference,
                null,
                decisionId.toString(),
                rationale,
                now);
        log.info("Decision recorded runId={} task={} kind={} decisionId={} parent={} actor={}/{}",
                runId, taskId, kind, decisionId, parentDecisionId, actorType, actorReference);
        return decision;
    }

    private void appendEvent(
            UUID runId,
            String taskId,
            ExecutionEventType eventType,
            ActorType actorType,
            String actorReference,
            String previousValue,
            String newValue,
            String details,
            Instant occurredAt) {
        requireOptionalText(details, "Event details", MAX_SUMMARY_LENGTH);
        var run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId));
        var previousEvent = eventRepository.findFirstByRunIdOrderBySequenceNumberDesc(runId);
        long expectedPreviousSequence = previousEvent.map(event -> event.getSequenceNumber()).orElse(0L);
        String expectedPreviousHash =
                previousEvent.map(event -> event.getEventHash()).orElse(AuditHashService.GENESIS_HASH);
        if (run.getAuditSequence() != expectedPreviousSequence
                || !run.getAuditHash().equals(expectedPreviousHash)) {
            throw new IllegalStateException("Audit chain tip does not match persisted events for run " + runId);
        }
        long sequenceNumber = run.getAuditSequence() + 1;
        String previousHash = run.getAuditHash();
        ExecutionEventEntity event = new ExecutionEventEntity(
                runId,
                sequenceNumber,
                taskId,
                eventType,
                actorType,
                actorReference,
                previousValue,
                newValue,
                details,
                occurredAt,
                previousHash,
                "");
        event.setEventHash(auditHashService.hash(event));
        eventRepository.save(event);
        run.updateAuditTip(sequenceNumber, event.getEventHash());
        runRepository.save(run);
    }

    private void validateEventInput(ActorType actorType, String actorReference, String details) {
        if (actorType == null) {
            throw new IllegalArgumentException("Actor type is required");
        }
        requireText(actorReference, "Actor reference", MAX_ACTOR_REFERENCE_LENGTH);
        requireOptionalText(details, "Event details", MAX_SUMMARY_LENGTH);
    }

    private void requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maximumLength + " characters");
        }
    }

    private void requireOptionalText(String value, String name, int maximumLength) {
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " cannot exceed " + maximumLength + " characters");
        }
    }

    private String hashForEvent(String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
