package com.schwab.agentic.orchestration.replan;

import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.TaskDag;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.DecisionRecordEntity;
import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.engine.OrchestrationRecordNotFoundException;
import com.schwab.agentic.orchestration.engine.SafeStopController;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DynamicReplanService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicReplanService.class);

    private static final Set<RunStatus> REPLANNABLE_STATUSES = Set.of(
            RunStatus.CREATED, RunStatus.SAFE_STOPPED, RunStatus.SUCCEEDED, RunStatus.ROLLED_BACK);

    private final DagValidator dagValidator;
    private final ExecutionRunRepository runRepository;
    private final TaskExecutionRepository taskRepository;
    private final DecisionRecordRepository decisionRepository;
    private final ApprovalService approvalService;
    private final OrchestrationPersistenceService persistenceService;
    private final SafeStopController safeStopController;

    public DynamicReplanService(
            DagValidator dagValidator,
            ExecutionRunRepository runRepository,
            TaskExecutionRepository taskRepository,
            DecisionRecordRepository decisionRepository,
            ApprovalService approvalService,
            OrchestrationPersistenceService persistenceService,
            SafeStopController safeStopController) {
        this.dagValidator = dagValidator;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.decisionRepository = decisionRepository;
        this.approvalService = approvalService;
        this.persistenceService = persistenceService;
        this.safeStopController = safeStopController;
    }

    @Transactional
    public ReplanResult replan(UUID runId, ReplanRequest request) {
        if (runId == null || request == null) {
            throw new IllegalArgumentException("Run ID and replan request are required");
        }
        TaskDag revisedDag = dagValidator.validate(request.tasks());
        ExecutionRunEntity run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId));
        if (!REPLANNABLE_STATUSES.contains(run.getStatus())) {
            throw new IllegalStateException("Run cannot be replanned from state " + run.getStatus());
        }
        if (safeStopController.isStopRequested(runId)) {
            throw new IllegalStateException("Clear the safe-stop request before replanning this run");
        }
        if (approvalService.hasPendingApproval(runId)) {
            throw new IllegalStateException("Resolve pending human approvals before replanning");
        }

        List<TaskExecutionEntity> persistedTasks = taskRepository.findAllByRunIdForUpdate(runId);
        if (persistedTasks.stream().anyMatch(task ->
                task.getState() == TaskState.RUNNING || task.getState() == TaskState.AWAITING_APPROVAL)) {
            throw new IllegalStateException("Active tasks or approval checkpoints prevent replanning");
        }
        Map<String, TaskExecutionEntity> allTasksById = new HashMap<>();
        persistedTasks.forEach(task -> allTasksById.put(task.getTaskId(), task));
        Map<String, TaskExecutionEntity> activeTasksById = new HashMap<>();
        persistedTasks.stream()
                .filter(task -> !task.isRetired())
                .forEach(task -> activeTasksById.put(task.getTaskId(), task));

        Set<String> addedTaskIds = new LinkedHashSet<>();
        Set<String> removedTaskIds = new LinkedHashSet<>();
        Set<String> changedDefinitionIds = new LinkedHashSet<>();
        Map<String, TaskDefinition> revisedDefinitions = revisedDag.tasksById();

        for (TaskDefinition revisedDefinition : revisedDefinitions.values()) {
            TaskExecutionEntity existing = allTasksById.get(revisedDefinition.taskId());
            if (existing == null) {
                addedTaskIds.add(revisedDefinition.taskId());
            } else if (existing.isRetired() || !sameDefinition(existing, revisedDefinition)) {
                changedDefinitionIds.add(revisedDefinition.taskId());
            }
        }
        for (TaskExecutionEntity existing : activeTasksById.values()) {
            if (!revisedDefinitions.containsKey(existing.getTaskId())) {
                removedTaskIds.add(existing.getTaskId());
            }
        }

        boolean inputChanged = request.updatedInputSummary() != null
                && !request.updatedInputSummary().equals(run.getInputSummary());
        boolean outputChanged = false;
        if (request.changedTaskId() != null) {
            TaskExecutionEntity upstream = activeTasksById.get(request.changedTaskId());
            TaskDefinition revisedSource = revisedDefinitions.get(request.changedTaskId());
            if (upstream == null || revisedSource == null || upstream.getState() != TaskState.SUCCEEDED) {
                throw new IllegalArgumentException("Only an active, succeeded task output can be revised");
            }
            if (!sameDefinition(upstream, revisedSource)) {
                throw new IllegalArgumentException("The revised upstream task definition must remain unchanged");
            }
            outputChanged = !request.updatedTaskOutput().equals(upstream.getResultSummary());
        }

        Set<String> recoveryTaskIds = new LinkedHashSet<>();
        if (run.getStatus() == RunStatus.ROLLED_BACK) {
            persistedTasks.stream()
                    .filter(task -> !task.isRetired())
                    .filter(task -> task.getState() == TaskState.ROLLED_BACK
                            || task.getState() == TaskState.SKIPPED
                            || task.getState() == TaskState.FAILED)
                    .map(TaskExecutionEntity::getTaskId)
                    .forEach(recoveryTaskIds::add);
        }
        if (changedDefinitionIds.isEmpty()
                && addedTaskIds.isEmpty()
                && removedTaskIds.isEmpty()
                && !inputChanged
                && !outputChanged
                && recoveryTaskIds.isEmpty()) {
            throw new IllegalArgumentException("Replan request does not change the run plan or any task output");
        }

        Set<String> invalidationSeeds = new LinkedHashSet<>(changedDefinitionIds);
        invalidationSeeds.addAll(recoveryTaskIds);
        if (inputChanged) {
            revisedDefinitions.values().stream()
                    .filter(definition -> definition.dependencyIds().isEmpty())
                    .map(TaskDefinition::taskId)
                    .forEach(invalidationSeeds::add);
        }
        Set<String> affectedTaskIds = descendantsIncludingSeeds(revisedDag, invalidationSeeds);
        if (outputChanged) {
            affectedTaskIds.addAll(descendantsOf(revisedDag, request.changedTaskId()));
        }

        Set<String> invalidatedTaskIds = new LinkedHashSet<>();
        Set<String> preservedTaskIds = new LinkedHashSet<>();
        for (String taskId : revisedDag.topologicalOrder()) {
            TaskExecutionEntity existing = allTasksById.get(taskId);
            if (existing == null) {
                continue;
            }
            TaskDefinition revisedDefinition = revisedDefinitions.get(taskId);
            if (changedDefinitionIds.contains(taskId)) {
                persistenceService.updateTaskDefinition(runId, taskId, revisedDefinition);
            }
            if (affectedTaskIds.contains(taskId)) {
                invalidateAndReset(runId, existing);
                invalidatedTaskIds.add(taskId);
            } else if (existing.getState() == TaskState.SUCCEEDED) {
                preservedTaskIds.add(taskId);
            }
        }

        for (String taskId : addedTaskIds) {
            persistenceService.addTask(runId, revisedDefinitions.get(taskId));
        }
        for (String taskId : removedTaskIds) {
            TaskExecutionEntity removed = allTasksById.get(taskId);
            if (removed.getState() != TaskState.SKIPPED) {
                if (removed.getState() != TaskState.INVALIDATED) {
                    persistenceService.transitionTask(
                            runId,
                            taskId,
                            TaskState.INVALIDATED,
                            ActorType.POLICY,
                            "dynamic-replanner",
                            "Task removed from revised DAG");
                }
                persistenceService.transitionTask(
                        runId,
                        taskId,
                        TaskState.SKIPPED,
                        ActorType.POLICY,
                        "dynamic-replanner",
                        "Retired because task is absent from revised DAG");
            }
            persistenceService.retireTask(runId, taskId);
        }

        DecisionRecordEntity parentDecision = findParentDecision(
                runId, request.changedTaskId(), changedDefinitionIds, removedTaskIds);
        persistenceService.transitionRun(
                runId, RunStatus.REPLANNING, ActorType.POLICY, "dynamic-replanner", "Applying validated DAG diff");
        DecisionRecordEntity decision = persistenceService.recordDecision(
                runId,
                request.changedTaskId() != null ? request.changedTaskId() : first(changedDefinitionIds, removedTaskIds),
                parentDecision == null ? null : parentDecision.getId(),
                DecisionKind.REPLAN,
                ActorType.HUMAN,
                request.requestedBy(),
                "inputChanged=" + inputChanged + "; outputChanged=" + outputChanged,
                request.rationale());
        if (inputChanged) {
            persistenceService.updateRunInputSummary(runId, request.updatedInputSummary(), request.requestedBy());
        }
        if (outputChanged) {
            persistenceService.reviseTaskOutput(
                    runId, request.changedTaskId(), request.updatedTaskOutput(), request.requestedBy());
        }
        persistenceService.transitionRun(
                runId, RunStatus.CREATED, ActorType.POLICY, "dynamic-replanner", "Replan is ready for execution");
        persistenceService.recordDagReplanned(
                runId,
                request.requestedBy(),
                "invalidated=" + invalidatedTaskIds.size()
                        + "; preserved=" + preservedTaskIds.size()
                        + "; added=" + addedTaskIds.size()
                        + "; removed=" + removedTaskIds.size());
        log.info("Replan applied runId={} invalidated={} preserved={} added={} removed={} decisionId={}",
                runId, invalidatedTaskIds.size(), preservedTaskIds.size(), addedTaskIds.size(),
                removedTaskIds.size(), decision.getId());

        return new ReplanResult(
                runId,
                RunStatus.CREATED,
                invalidatedTaskIds,
                preservedTaskIds,
                addedTaskIds,
                removedTaskIds,
                decision.getId());
    }

    private void invalidateAndReset(UUID runId, TaskExecutionEntity task) {
        TaskState current = taskRepository.findByRunIdAndTaskId(runId, task.getTaskId())
                .orElseThrow()
                .getState();
        if (current == TaskState.PENDING) {
            return;
        }
        if (current != TaskState.INVALIDATED) {
            persistenceService.transitionTask(
                    runId,
                    task.getTaskId(),
                    TaskState.INVALIDATED,
                    ActorType.POLICY,
                    "dynamic-replanner",
                    "Task output invalidated by a changed requirement, upstream output, or task definition");
        }
        persistenceService.transitionTask(
                runId,
                task.getTaskId(),
                TaskState.PENDING,
                ActorType.POLICY,
                "dynamic-replanner",
                "Task reset for execution under revised DAG");
    }

    private Set<String> descendantsIncludingSeeds(TaskDag dag, Set<String> seeds) {
        Set<String> affected = new LinkedHashSet<>();
        Map<String, List<String>> dependents = dependentsOf(dag);
        ArrayDeque<String> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            String taskId = queue.removeFirst();
            if (!dag.tasksById().containsKey(taskId) || !affected.add(taskId)) {
                continue;
            }
            dependents.getOrDefault(taskId, List.of()).forEach(queue::addLast);
        }
        return affected;
    }

    private Set<String> descendantsOf(TaskDag dag, String taskId) {
        Map<String, List<String>> dependents = dependentsOf(dag);
        Set<String> descendants = new LinkedHashSet<>();
        dependents.getOrDefault(taskId, List.of()).forEach(descendants::add);
        ArrayDeque<String> queue = new ArrayDeque<>(descendants);
        while (!queue.isEmpty()) {
            dependents.getOrDefault(queue.removeFirst(), List.of()).forEach(dependent -> {
                if (descendants.add(dependent)) {
                    queue.addLast(dependent);
                }
            });
        }
        return descendants;
    }

    private Map<String, List<String>> dependentsOf(TaskDag dag) {
        Map<String, List<String>> dependents = new HashMap<>();
        dag.tasksById().keySet().forEach(taskId -> dependents.put(taskId, new ArrayList<>()));
        dag.tasksById().values().forEach(definition -> definition.dependencyIds().forEach(dependencyId ->
                dependents.get(dependencyId).add(definition.taskId())));
        return dependents;
    }

    private DecisionRecordEntity findParentDecision(
            UUID runId, String changedTaskId, Set<String> changedDefinitionIds, Set<String> removedTaskIds) {
        if (changedTaskId != null) {
            return decisionRepository
                    .findFirstByRunIdAndTaskIdOrderByCreatedAtDescIdDesc(runId, changedTaskId)
                    .orElse(null);
        }
        String taskId = first(changedDefinitionIds, removedTaskIds);
        return taskId == null
                ? null
                : decisionRepository
                        .findFirstByRunIdAndTaskIdOrderByCreatedAtDescIdDesc(runId, taskId)
                        .orElse(null);
    }

    @SafeVarargs
    private final String first(Set<String>... taskGroups) {
        for (Set<String> taskGroup : taskGroups) {
            if (!taskGroup.isEmpty()) {
                return taskGroup.iterator().next();
            }
        }
        return null;
    }

    private boolean sameDefinition(TaskExecutionEntity task, TaskDefinition definition) {
        return task.getKind() == definition.kind()
                && task.getImpactLevel() == definition.impactLevel()
                && task.getDependencyIds().equals(definition.dependencyIds());
    }
}
