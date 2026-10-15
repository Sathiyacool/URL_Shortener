package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.approval.ApprovalRequestEntity;
import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.approval.ApprovalStatus;
import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDag;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.DecisionRecordEntity;
import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExecutionEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExecutionEngine.class);
    private static final long COMPLETION_POLL_MILLIS = 50;

    private final ExecutorService taskExecutor;
    private final int maxParallelTasks;
    private final Duration safeStopTimeout;
    private final RetryPolicy retryPolicy;
    private final TaskHandlerRegistry handlerRegistry;
    private final ApprovalService approvalService;
    private final OrchestrationPersistenceService persistenceService;
    private final ExecutionRunRepository runRepository;
    private final TaskExecutionRepository taskRepository;
    private final DagValidator dagValidator;
    private final SafeStopController safeStopController;

    public ExecutionEngine(
            ExecutorService taskExecutor,
            @Value("${orchestration.execution.max-parallel-tasks:4}") int maxParallelTasks,
            @Value("${orchestration.execution.safe-stop-timeout:2s}") Duration safeStopTimeout,
            RetryPolicy retryPolicy,
            TaskHandlerRegistry handlerRegistry,
            ApprovalService approvalService,
            OrchestrationPersistenceService persistenceService,
            ExecutionRunRepository runRepository,
            TaskExecutionRepository taskRepository,
            DagValidator dagValidator,
            SafeStopController safeStopController) {
        if (maxParallelTasks < 1 || safeStopTimeout.isNegative() || safeStopTimeout.isZero()) {
            throw new IllegalArgumentException("Parallel task count and safe-stop timeout must be positive");
        }
        this.taskExecutor = taskExecutor;
        this.maxParallelTasks = maxParallelTasks;
        this.safeStopTimeout = safeStopTimeout;
        this.retryPolicy = retryPolicy;
        this.handlerRegistry = handlerRegistry;
        this.approvalService = approvalService;
        this.persistenceService = persistenceService;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.dagValidator = dagValidator;
        this.safeStopController = safeStopController;
    }

    public ExecutionSummary execute(UUID runId) {
        return execute(runId, ExecutionMode.PARALLEL);
    }

    public ExecutionSummary execute(UUID runId, ExecutionMode executionMode) {
        if (executionMode == null) {
            throw new IllegalArgumentException("Execution mode is required");
        }
        int runParallelism = executionMode == ExecutionMode.SEQUENTIAL ? 1 : maxParallelTasks;
        ExecutionRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId));
        if (run.getStatus() != RunStatus.CREATED
                && run.getStatus() != RunStatus.SAFE_STOPPED
                && run.getStatus() != RunStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("Run is not executable from state " + run.getStatus());
        }
        if (run.getStatus() == RunStatus.SAFE_STOPPED && safeStopController.isStopRequested(runId)) {
            return summary(runId, RunStatus.SAFE_STOPPED, null);
        }
        if (run.getStatus() == RunStatus.AWAITING_APPROVAL && approvalService.hasPendingApproval(runId)) {
            return summary(runId, RunStatus.AWAITING_APPROVAL, null);
        }

        TaskDag dag = loadDag(runId);
        log.info("Execution starting runId={} mode={} parallelism={} tasks={}",
                runId, executionMode, runParallelism, dag.topologicalOrder().size());
        try {
            persistenceService.transitionRun(
                    runId, RunStatus.RUNNING, ActorType.SYSTEM, "execution-engine", "Run execution started");
        } catch (InvalidStateTransitionException exception) {
            RunStatus currentStatus = runRepository.findById(runId)
                    .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId))
                    .getStatus();
            throw new IllegalStateException("Run is not executable from state " + currentStatus, exception);
        }

        CompletionService<TaskOutcome> completions = new ExecutorCompletionService<>(taskExecutor);
        Map<Future<TaskOutcome>, ActiveTask> activeTasks = new HashMap<>();
        while (true) {
            if (safeStopController.isStopRequested(runId)) {
                return stopRun(runId, activeTasks);
            }

            List<TaskExecutionEntity> currentTasks = loadTasks(runId);
            boolean hasFailedTask = currentTasks.stream().anyMatch(task -> task.getState() == TaskState.FAILED);
            if (!hasFailedTask) {
                boolean approvalPending =
                        scheduleReadyTasks(runId, dag, currentTasks, activeTasks, completions, runParallelism);
                if (approvalPending) {
                    RunStatus status = runRepository.findById(runId).orElseThrow().getStatus();
                    return summary(runId, status, null);
                }
                currentTasks = loadTasks(runId);
                hasFailedTask = currentTasks.stream().anyMatch(task -> task.getState() == TaskState.FAILED);
            }

            if (hasFailedTask && activeTasks.isEmpty()) {
                return rollbackFailedRun(runId, dag, currentTasks);
            }
            if (!hasFailedTask && activeTasks.isEmpty()) {
                if (currentTasks.stream().allMatch(task ->
                        task.getState() == TaskState.SUCCEEDED || task.getState() == TaskState.SKIPPED)) {
                    persistenceService.transitionRun(
                            runId, RunStatus.SUCCEEDED, ActorType.SYSTEM, "execution-engine", "All tasks completed");
                    return summary(runId, RunStatus.SUCCEEDED, null);
                }
                throw new IllegalStateException("DAG scheduler stalled with unfinished tasks and no active work");
            }

            try {
                Future<TaskOutcome> completed = completions.poll(COMPLETION_POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (completed != null) {
                    ActiveTask activeTask = activeTasks.remove(completed);
                    try {
                        TaskOutcome outcome = completed.get();
                        if (outcome.status() == TaskOutcomeStatus.STOPPED
                                && !safeStopController.isStopRequested(runId)) {
                            failUnexpectedTask(runId, activeTask, "Task stopped without a safe-stop request");
                        }
                    } catch (CancellationException exception) {
                        if (!safeStopController.isStopRequested(runId)) {
                            failUnexpectedTask(runId, activeTask, "Task future was cancelled unexpectedly");
                        }
                    } catch (ExecutionException exception) {
                        failUnexpectedTask(runId, activeTask, describe(exception.getCause()));
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                safeStopController.requestRunStop(runId);
            }
        }
    }

    private TaskDag loadDag(UUID runId) {
        List<TaskExecutionEntity> tasks = loadTasks(runId);
        List<TaskDefinition> definitions = tasks.stream()
                .filter(task -> !task.isRetired())
                .map(task -> new TaskDefinition(
                        task.getTaskId(),
                        task.getKind(),
                        task.getDependencyIds(),
                        task.getImpactLevel()))
                .toList();
        return dagValidator.validate(definitions);
    }

    private boolean scheduleReadyTasks(
            UUID runId,
            TaskDag dag,
            List<TaskExecutionEntity> currentTasks,
            Map<Future<TaskOutcome>, ActiveTask> activeTasks,
            CompletionService<TaskOutcome> completions,
            int runParallelism) {
        Map<String, TaskExecutionEntity> tasksById = new HashMap<>();
        currentTasks.forEach(task -> tasksById.put(task.getTaskId(), task));
        Set<String> activeTaskIds = new HashSet<>();
        activeTasks.values().forEach(task -> activeTaskIds.add(task.taskId));

        for (String taskId : dag.topologicalOrder()) {
            if (activeTasks.size() >= runParallelism || safeStopController.isStopRequested(runId)) {
                return false;
            }
            if (activeTaskIds.contains(taskId)) {
                continue;
            }
            TaskExecutionEntity task = tasksById.get(taskId);
            if (task.getState() == TaskState.PENDING
                    && task.getDependencyIds().stream().allMatch(dependencyId -> {
                        TaskExecutionEntity dependency = tasksById.get(dependencyId);
                        return dependency != null && dependency.getState() == TaskState.SUCCEEDED;
                    })) {
                if (task.getImpactLevel() == ImpactLevel.HIGH) {
                    if (!activeTasks.isEmpty()) {
                        return false;
                    }
                    ApprovalRequestEntity approval = approvalService.requestApproval(
                            runId,
                            taskId,
                            "Task " + taskId + " is classified HIGH impact and requires human approval");
                    if (approval.getStatus() == ApprovalStatus.PENDING
                            || approval.getStatus() == ApprovalStatus.REJECTED) {
                        log.info("Approval gate holding runId={} task={} approvalStatus={}",
                                runId, taskId, approval.getStatus());
                        return true;
                    }
                    task = taskRepository.findByRunIdAndTaskId(runId, taskId).orElseThrow();
                    tasksById.put(taskId, task);
                }
            }
            if (task.getState() == TaskState.PENDING
                    && task.getDependencyIds().stream().allMatch(dependencyId -> {
                        TaskExecutionEntity dependency = tasksById.get(dependencyId);
                        return dependency != null && dependency.getState() == TaskState.SUCCEEDED;
                    })) {
                persistenceService.transitionTask(
                        runId, taskId, TaskState.READY, ActorType.SYSTEM, "scheduler", "All dependencies succeeded");
                task = taskRepository.findByRunIdAndTaskId(runId, taskId).orElseThrow();
                tasksById.put(taskId, task);
            }
            if (task.getState() != TaskState.READY && task.getState() != TaskState.RETRYING) {
                continue;
            }

            ActiveTask activeTask = new ActiveTask(taskId);
            try {
                Future<TaskOutcome> future = completions.submit(() -> {
                    synchronized (activeTask) {
                        if (activeTask.cancelledBeforeStart) {
                            return TaskOutcome.stopped(taskId);
                        }
                        activeTask.started = true;
                    }
                    try {
                        return executeTask(runId, taskId);
                    } finally {
                        activeTask.exited.countDown();
                    }
                });
                activeTasks.put(future, activeTask);
                activeTaskIds.add(taskId);
                log.debug("Task dispatched runId={} task={} activeTasks={}", runId, taskId, activeTasks.size());
            } catch (RejectedExecutionException exception) {
                persistenceService.transitionTask(
                        runId,
                        taskId,
                        TaskState.FAILED,
                        ActorType.SYSTEM,
                        "execution-engine",
                        "Bounded task executor rejected work: " + exception.getMessage());
                return false;
            }
        }
        return false;
    }

    private TaskOutcome executeTask(UUID runId, String taskId) {
        TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, taskId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                        "Task not found in run " + runId + ": " + taskId));
        persistenceService.transitionTask(
                runId, taskId, TaskState.RUNNING, ActorType.SYSTEM, "execution-engine", "Task attempt started");
        TaskExecutionEntity runningTask = taskRepository.findByRunIdAndTaskId(runId, taskId).orElseThrow();
        TaskContext context = buildContext(runId, runningTask);

        TaskHandler handler;
        try {
            handler = handlerRegistry.handlerFor(runningTask.getKind());
        } catch (MissingTaskHandlerException exception) {
            persistenceService.transitionTask(
                    runId,
                    taskId,
                    TaskState.FAILED,
                    ActorType.SYSTEM,
                    "execution-engine",
                    exception.getMessage());
            return TaskOutcome.failed(taskId, exception.getMessage());
        }

        UUID parentDecisionId = null;
        Exception lastFailure = null;
        while (true) {
            if (context.isStopRequested()) {
                return TaskOutcome.stopped(taskId);
            }
            try {
                TaskResult result = handler.execute(context);
                if (result == null) {
                    throw new IllegalStateException("Task handler returned no result");
                }
                persistenceService.transitionTask(
                        runId,
                        taskId,
                        TaskState.SUCCEEDED,
                        ActorType.AGENT,
                        actorReference(handler),
                        "Task completed successfully",
                        result.output());
                return TaskOutcome.succeeded(taskId);
            } catch (TaskStoppedException exception) {
                return TaskOutcome.stopped(taskId);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return TaskOutcome.stopped(taskId);
            } catch (Exception exception) {
                if (context.isStopRequested()) {
                    return TaskOutcome.stopped(taskId);
                }
                lastFailure = exception;
                int attempts = taskRepository.findByRunIdAndTaskId(runId, taskId).orElseThrow().getAttemptCount();
                if (!retryPolicy.shouldRetry(attempts, exception)) {
                    log.warn("Retries exhausted runId={} task={} attempts={} error={}",
                            runId, taskId, attempts, describe(exception));
                    break;
                }

                String failureDetails = describe(exception);
                log.warn("Retrying runId={} task={} failedAttempt={} error={}",
                        runId, taskId, attempts, failureDetails);
                persistenceService.transitionTask(
                        runId,
                        taskId,
                        TaskState.RETRYING,
                        ActorType.SYSTEM,
                        "retry-policy",
                        "Attempt " + attempts + " failed: " + failureDetails);
                DecisionRecordEntity retryDecision = persistenceService.recordDecision(
                        runId,
                        taskId,
                        parentDecisionId,
                        DecisionKind.RETRY,
                        ActorType.POLICY,
                        "retry-policy",
                        "attempt=" + attempts,
                        "Retrying after: " + failureDetails);
                parentDecisionId = retryDecision.getId();

                try {
                    delay(retryPolicy.delayBeforeAttempt(attempts + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return TaskOutcome.stopped(taskId);
                }
                if (context.isStopRequested()) {
                    return TaskOutcome.stopped(taskId);
                }
                persistenceService.transitionTask(
                        runId, taskId, TaskState.RUNNING, ActorType.SYSTEM, "retry-policy", "Retry attempt started");
            }
        }

        var fallback = handlerRegistry.fallbackFor(runningTask.getKind());
        if (fallback.isPresent()) {
            String failureDetails = describe(lastFailure);
            log.warn("Invoking fallback runId={} task={}", runId, taskId);
            DecisionRecordEntity fallbackDecision = persistenceService.recordDecision(
                    runId,
                    taskId,
                    parentDecisionId,
                    DecisionKind.FALLBACK,
                    ActorType.POLICY,
                    "fallback-policy",
                    "primaryFailure=" + failureDetails,
                    "Invoking registered fallback handler");
            try {
                TaskResult fallbackResult = fallback.get().execute(context, lastFailure);
                if (fallbackResult == null) {
                    throw new IllegalStateException("Fallback handler returned no result");
                }
                persistenceService.transitionTask(
                        runId,
                        taskId,
                        TaskState.SUCCEEDED,
                        ActorType.AGENT,
                        actorReference(fallback.get()),
                        "Registered fallback completed successfully",
                        fallbackResult.output());
                return TaskOutcome.succeeded(taskId);
            } catch (TaskStoppedException exception) {
                return TaskOutcome.stopped(taskId);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return TaskOutcome.stopped(taskId);
            } catch (Exception exception) {
                lastFailure = exception;
            }
        }

        String failureDetails = describe(lastFailure);
        persistenceService.transitionTask(
                runId,
                taskId,
                TaskState.FAILED,
                ActorType.AGENT,
                handler.getClass().getSimpleName(),
                failureDetails);
        return TaskOutcome.failed(taskId, failureDetails);
    }

    private TaskContext buildContext(UUID runId, TaskExecutionEntity task) {
        Map<String, String> dependencyOutputs = new HashMap<>();
        for (String dependencyId : task.getDependencyIds()) {
            TaskExecutionEntity dependency = taskRepository.findByRunIdAndTaskId(runId, dependencyId)
                    .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                            "Dependency not found in run " + runId + ": " + dependencyId));
            if (dependency.getState() != TaskState.SUCCEEDED) {
                throw new IllegalStateException("Task dependency has not succeeded: " + dependencyId);
            }
            dependencyOutputs.put(dependencyId, dependency.getResultSummary());
        }
        ExecutionRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId));
        String approvalRationale = approvalService
                .latestForTask(runId, task.getTaskId())
                .filter(request -> request.getStatus() == ApprovalStatus.APPROVED)
                .map(ApprovalRequestEntity::getDecisionRationale)
                .orElse(null);
        return new TaskContext(
                runId,
                task.getTaskId(),
                run.getInputSummary(),
                dependencyOutputs,
                approvalRationale,
                () -> safeStopController.isStopRequested(runId));
    }

    private ExecutionSummary stopRun(UUID runId, Map<Future<TaskOutcome>, ActiveTask> activeTasks) {
        log.warn("Safe-stop honored runId={} activeTasks={}", runId, activeTasks.size());
        boolean restoreInterrupt = Thread.interrupted();
        try {
            for (Map.Entry<Future<TaskOutcome>, ActiveTask> entry : activeTasks.entrySet()) {
                ActiveTask activeTask = entry.getValue();
                synchronized (activeTask) {
                    if (!activeTask.started) {
                        activeTask.cancelledBeforeStart = true;
                        activeTask.exited.countDown();
                    }
                    entry.getKey().cancel(true);
                }
            }
            long stopDeadline = System.nanoTime() + safeStopTimeout.toNanos();
            for (ActiveTask activeTask : activeTasks.values()) {
                long remainingNanos = stopDeadline - System.nanoTime();
                if (remainingNanos <= 0
                        || !activeTask.exited.await(remainingNanos, TimeUnit.NANOSECONDS)) {
                    throw new SafeStopTimeoutException(runId);
                }
            }

            List<TaskExecutionEntity> tasks = loadTasks(runId);
            if (tasks.stream().anyMatch(task -> task.getState() == TaskState.FAILED)) {
                return rollbackFailedRun(runId, loadDag(runId), tasks);
            }
            for (TaskExecutionEntity task : tasks) {
                if (task.getState() == TaskState.RUNNING) {
                    persistenceService.transitionTask(
                            runId,
                            task.getTaskId(),
                            TaskState.RETRYING,
                            ActorType.SYSTEM,
                            "safe-stop",
                            "Interrupted by safe-stop; task may be resumed");
                }
            }
            persistenceService.transitionRun(
                    runId, RunStatus.SAFE_STOPPED, ActorType.SYSTEM, "safe-stop", "Run stopped cooperatively");
            return summary(runId, RunStatus.SAFE_STOPPED, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for tasks to stop", exception);
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ExecutionSummary rollbackFailedRun(
            UUID runId, TaskDag dag, List<TaskExecutionEntity> currentTasks) {
        String failureReason = currentTasks.stream()
                .filter(task -> task.getState() == TaskState.FAILED)
                .map(TaskExecutionEntity::getFailureSummary)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("A task failed");

        persistenceService.transitionRun(
                runId, RunStatus.FAILED, ActorType.SYSTEM, "execution-engine", failureReason);
        log.error("Run failed runId={} reason={}", runId, failureReason);
        for (TaskExecutionEntity task : currentTasks) {
            if (task.getState() == TaskState.PENDING || task.getState() == TaskState.READY) {
                persistenceService.transitionTask(
                        runId,
                        task.getTaskId(),
                        TaskState.SKIPPED,
                        ActorType.SYSTEM,
                        "execution-engine",
                        "Skipped because the run failed");
            } else if (task.getState() == TaskState.RETRYING) {
                persistenceService.transitionTask(
                        runId,
                        task.getTaskId(),
                        TaskState.SKIPPED,
                        ActorType.SYSTEM,
                        "execution-engine",
                        "Skipped because the run failed");
            }
        }

        List<TaskExecutionEntity> succeededTasks = new ArrayList<>();
        List<String> reverseOrder = new ArrayList<>(dag.topologicalOrder());
        Collections.reverse(reverseOrder);
        for (String taskId : reverseOrder) {
            TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, taskId).orElseThrow();
            if (task.getState() == TaskState.SUCCEEDED) {
                succeededTasks.add(task);
            }
        }
        if (succeededTasks.isEmpty()) {
            return summary(runId, RunStatus.FAILED, failureReason);
        }

        persistenceService.transitionRun(
                runId, RunStatus.ROLLING_BACK, ActorType.SYSTEM, "rollback-manager", "Compensating completed tasks");
        log.warn("Rollback starting runId={} tasksToCompensate={}", runId, succeededTasks.size());
        boolean rollbackSucceeded = true;
        for (TaskExecutionEntity task : succeededTasks) {
            try {
                TaskHandler handler = handlerRegistry.handlerFor(task.getKind());
                handler.compensate(buildContext(runId, task), new TaskResult(task.getResultSummary()));
                persistenceService.transitionTask(
                        runId,
                        task.getTaskId(),
                        TaskState.ROLLED_BACK,
                        ActorType.SYSTEM,
                        "rollback-manager",
                        "Compensation completed");
            } catch (Exception exception) {
                rollbackSucceeded = false;
                String compensationFailure = "Compensation failed for " + task.getTaskId() + ": " + describe(exception);
                persistenceService.recordDecision(
                        runId,
                        task.getTaskId(),
                        null,
                        DecisionKind.ROLLBACK,
                        ActorType.SYSTEM,
                        "rollback-manager",
                        failureReason,
                        compensationFailure);
            }
        }
        RunStatus finalStatus = rollbackSucceeded ? RunStatus.ROLLED_BACK : RunStatus.FAILED;
        persistenceService.transitionRun(
                runId,
                finalStatus,
                ActorType.SYSTEM,
                "rollback-manager",
                rollbackSucceeded ? "All compensations completed" : "One or more compensations failed");
        String finalFailureReason = rollbackSucceeded
                ? failureReason
                : failureReason + "; one or more compensations failed";
        return summary(runId, finalStatus, finalFailureReason);
    }

    private void failUnexpectedTask(UUID runId, ActiveTask activeTask, String reason) {
        if (activeTask == null) {
            throw new IllegalStateException("Completed task could not be identified");
        }
        TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, activeTask.taskId).orElseThrow();
        if (task.getState() == TaskState.RUNNING
                || task.getState() == TaskState.READY
                || task.getState() == TaskState.RETRYING) {
            persistenceService.transitionTask(
                    runId,
                    activeTask.taskId,
                    TaskState.FAILED,
                    ActorType.SYSTEM,
                    "execution-engine",
                    reason);
        }
    }

    private List<TaskExecutionEntity> loadTasks(UUID runId) {
        return taskRepository.findAllByRunIdOrderByTaskIdAsc(runId);
    }

    private ExecutionSummary summary(UUID runId, RunStatus status, String failureReason) {
        Map<String, Integer> counts = new HashMap<>();
        for (TaskExecutionEntity task : loadTasks(runId)) {
            counts.merge(task.getState().name(), 1, Integer::sum);
        }
        log.info("Execution returned runId={} status={} taskStates={} failureReason={}",
                runId, status, counts, failureReason);
        return new ExecutionSummary(runId, status, counts, failureReason);
    }

    private String describe(Throwable failure) {
        if (failure == null) {
            return "Task failed without an exception detail";
        }
        String message = failure.getMessage();
        String description = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return description.length() <= 4000 ? description : description.substring(0, 4000);
    }

    private String actorReference(Object handler) {
        String simpleName = handler.getClass().getSimpleName();
        String name = simpleName.isBlank() ? handler.getClass().getName() : simpleName;
        return name.length() <= 128 ? name : name.substring(name.length() - 128);
    }

    private void delay(Duration duration) throws InterruptedException {
        long milliseconds = duration.toMillis();
        int nanoseconds = (int) duration.minusMillis(milliseconds).toNanos();
        if (milliseconds > 0 || nanoseconds > 0) {
            Thread.sleep(milliseconds, nanoseconds);
        }
    }

    private enum TaskOutcomeStatus {
        SUCCEEDED,
        FAILED,
        STOPPED
    }

    private record TaskOutcome(String taskId, TaskOutcomeStatus status, String details) {

        static TaskOutcome succeeded(String taskId) {
            return new TaskOutcome(taskId, TaskOutcomeStatus.SUCCEEDED, null);
        }

        static TaskOutcome failed(String taskId, String details) {
            return new TaskOutcome(taskId, TaskOutcomeStatus.FAILED, details);
        }

        static TaskOutcome stopped(String taskId) {
            return new TaskOutcome(taskId, TaskOutcomeStatus.STOPPED, null);
        }
    }

    private static final class ActiveTask {

        private final String taskId;
        private final CountDownLatch exited = new CountDownLatch(1);
        private boolean started;
        private boolean cancelledBeforeStart;

        private ActiveTask(String taskId) {
            this.taskId = taskId;
        }
    }
}
