package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.approval.ApprovalDecision;
import com.schwab.agentic.orchestration.approval.ApprovalRequestRepository;
import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.approval.ApprovalStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ExecutionEngineIntegrationTest {

    @Autowired
    private ExecutionEngine executionEngine;

    @Autowired
    private OrchestrationPersistenceService persistenceService;

    @Autowired
    private TaskExecutionRepository taskRepository;

    @Autowired
    private ExecutionRunRepository runRepository;

    @Autowired
    private ExecutionEventRepository eventRepository;

    @Autowired
    private DecisionRecordRepository decisionRepository;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRequestRepository approvalRepository;

    @Autowired
    private SafeStopController safeStopController;

    @Autowired
    private RetryPolicy retryPolicy;

    @Autowired
    private DagValidator dagValidator;

    @MockitoBean
    private TaskHandlerRegistry handlerRegistry;

    @BeforeEach
    void clearState() {
        safeStopController.clearGlobalStop();
        reset(handlerRegistry);
        decisionRepository.deleteAll();
        approvalRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void runsIndependentTasksInParallelAndWaitsForDependencies() {
        CountDownLatch independentTasksStarted = new CountDownLatch(2);
        AtomicInteger runningCount = new AtomicInteger();
        AtomicInteger maximumRunningCount = new AtomicInteger();
        AtomicReference<Map<String, String>> observedDependencies = new AtomicReference<>();
        TaskHandler handler = new TaskHandler() {
            @Override
            public TaskKind kind() {
                return TaskKind.CUSTOM;
            }

            @Override
            public TaskResult execute(TaskContext context) throws Exception {
                if (context.taskId().equals("join")) {
                    observedDependencies.set(context.dependencyOutputs());
                    return TaskResult.completed("joined");
                }
                int running = runningCount.incrementAndGet();
                maximumRunningCount.accumulateAndGet(running, Math::max);
                independentTasksStarted.countDown();
                try {
                    if (!independentTasksStarted.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Independent tasks did not overlap");
                    }
                    return TaskResult.completed(context.taskId() + "-output");
                } finally {
                    runningCount.decrementAndGet();
                }
            }
        };
        register(handler);

        UUID runId = createRun(List.of(
                task("left", Set.of()),
                task("right", Set.of()),
                task("join", Set.of("left", "right"))));
        ExecutionSummary summary = executionEngine.execute(runId);

        assertEquals(RunStatus.SUCCEEDED, summary.status(), summary.toString());
        assertEquals(3, summary.taskCounts().get(TaskState.SUCCEEDED.name()));
        assertTrue(maximumRunningCount.get() >= 2);
        assertEquals("joined", findTask(runId, "join").getResultSummary());
        assertEquals(Map.of("left", "left-output", "right", "right-output"), observedDependencies.get());
    }

    @Test
    void retriesOnlyUpToConfiguredLimitAndRecordsEachRetryDecision() {
        AtomicInteger attempts = new AtomicInteger();
        register(handler(context -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary provider failure");
            }
            return TaskResult.completed("recovered");
        }, null));

        UUID runId = createRun(List.of(task("single", Set.of())));
        ExecutionSummary summary = executionEngine.execute(runId);

        assertEquals(RunStatus.SUCCEEDED, summary.status(), summary.toString());
        assertEquals(3, findTask(runId, "single").getAttemptCount());
        assertEquals(
                2,
                decisionRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(runId).stream()
                        .filter(decision -> decision.getKind() == DecisionKind.RETRY)
                        .count());
    }

    @Test
    void usesRegisteredFallbackAfterPrimaryRetriesAreExhausted() {
        AtomicInteger primaryAttempts = new AtomicInteger();
        AtomicInteger fallbackAttempts = new AtomicInteger();
        register(handler(context -> {
            primaryAttempts.incrementAndGet();
            throw new IllegalStateException("primary unavailable");
        }, null));
        when(handlerRegistry.fallbackFor(TaskKind.CUSTOM)).thenReturn(Optional.of(new TaskFallbackHandler() {
            @Override
            public TaskKind kind() {
                return TaskKind.CUSTOM;
            }

            @Override
            public TaskResult execute(TaskContext context, Exception primaryFailure) {
                fallbackAttempts.incrementAndGet();
                return TaskResult.completed("fallback-output");
            }
        }));

        UUID runId = createRun(List.of(task("single", Set.of())));
        ExecutionSummary summary = executionEngine.execute(runId);

        assertEquals(RunStatus.SUCCEEDED, summary.status(), summary.toString());
        assertEquals(3, primaryAttempts.get());
        assertEquals(1, fallbackAttempts.get());
        assertEquals("fallback-output", findTask(runId, "single").getResultSummary());
        assertTrue(decisionRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(runId).stream()
                .anyMatch(decision -> decision.getKind() == DecisionKind.FALLBACK));
    }

    @Test
    void compensatesSucceededTasksInReverseTopologicalOrderAfterFailure() {
        List<String> compensationOrder = new java.util.concurrent.CopyOnWriteArrayList<>();
        TaskHandler handler = new TaskHandler() {
            @Override
            public TaskKind kind() {
                return TaskKind.CUSTOM;
            }

            @Override
            public TaskResult execute(TaskContext context) {
                if (context.taskId().equals("c")) {
                    throw new IllegalStateException("terminal failure");
                }
                return TaskResult.completed(context.taskId() + "-output");
            }

            @Override
            public void compensate(TaskContext context, TaskResult completedResult) {
                compensationOrder.add(context.taskId());
            }
        };
        register(handler);

        UUID runId = createRun(List.of(
                task("a", Set.of()),
                task("b", Set.of("a")),
                task("c", Set.of("b"))));
        ExecutionSummary summary = executionEngine.execute(runId);

        assertEquals(RunStatus.ROLLED_BACK, summary.status(), summary.toString());
        assertEquals(List.of("b", "a"), compensationOrder);
        assertEquals(TaskState.ROLLED_BACK, findTask(runId, "a").getState());
        assertEquals(TaskState.ROLLED_BACK, findTask(runId, "b").getState());
        assertEquals(TaskState.FAILED, findTask(runId, "c").getState());
    }

    @Test
    void safeStopInterruptsWorkAndAllowsExplicitResume() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        register(handler(context -> {
            taskStarted.countDown();
            while (true) {
                context.checkStopRequested();
                Thread.sleep(10);
            }
        }, null));

        UUID runId = createRun(List.of(task("single", Set.of())));
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<ExecutionSummary> stoppedRun = caller.submit(() -> executionEngine.execute(runId));
            assertTrue(taskStarted.await(3, TimeUnit.SECONDS));
            safeStopController.requestRunStop(runId);

            assertEquals(RunStatus.SAFE_STOPPED, stoppedRun.get(3, TimeUnit.SECONDS).status());
            assertEquals(TaskState.RETRYING, findTask(runId, "single").getState());

            safeStopController.clearRunStop(runId);
            register(handler(context -> TaskResult.completed("resumed"), null));
            ExecutionSummary resumed = executionEngine.execute(runId);
            assertEquals(RunStatus.SUCCEEDED, resumed.status(), resumed.toString());
            assertEquals(2, findTask(runId, "single").getAttemptCount());
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void supportsSequentialExecutionWithOneActiveTask() {
        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maximumActiveCount = new AtomicInteger();
        register(handler(context -> {
            int active = activeCount.incrementAndGet();
            maximumActiveCount.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(25);
                return TaskResult.completed(context.taskId());
            } finally {
                activeCount.decrementAndGet();
            }
        }, null));

        UUID runId = createRun(List.of(task("first", Set.of()), task("second", Set.of())));
        ExecutorService sequentialExecutor = Executors.newSingleThreadExecutor();
        try {
            ExecutionEngine sequentialEngine = new ExecutionEngine(
                    sequentialExecutor,
                    1,
                    Duration.ofSeconds(2),
                    retryPolicy,
                    handlerRegistry,
                    approvalService,
                    persistenceService,
                    runRepository,
                    taskRepository,
                    dagValidator,
                    safeStopController);

            assertEquals(RunStatus.SUCCEEDED, sequentialEngine.execute(runId).status());
            assertEquals(1, maximumActiveCount.get());
        } finally {
            sequentialExecutor.shutdownNow();
        }
    }

    @Test
    void rejectsConcurrentExecutionOfAnAlreadyRunningRun() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowTaskToFinish = new CountDownLatch(1);
        register(handler(context -> {
            executions.incrementAndGet();
            taskStarted.countDown();
            if (!allowTaskToFinish.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent execution test timed out");
            }
            return TaskResult.completed("completed-once");
        }, null));
        UUID runId = createRun(List.of(task("single", Set.of())));
        ExecutorService callers = Executors.newFixedThreadPool(2);

        try {
            Future<ExecutionSummary> firstExecution = callers.submit(() -> executionEngine.execute(runId));
            assertTrue(taskStarted.await(3, TimeUnit.SECONDS), "The first execution should dispatch its task");

            Future<ExecutionSummary> duplicateExecution = callers.submit(() -> executionEngine.execute(runId));
            ExecutionException duplicateFailure =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            ExecutionException.class, () -> duplicateExecution.get(3, TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalStateException.class, duplicateFailure.getCause());

            allowTaskToFinish.countDown();
            assertEquals(RunStatus.SUCCEEDED, firstExecution.get(3, TimeUnit.SECONDS).status());
            assertEquals(1, executions.get());
        } finally {
            allowTaskToFinish.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void globalSafeStopCanHoldAndThenResumeANewRun() {
        safeStopController.requestGlobalStop();
        UUID runId = createRun(List.of(task("single", Set.of())));

        assertEquals(RunStatus.SAFE_STOPPED, executionEngine.execute(runId).status());
        assertEquals(TaskState.PENDING, findTask(runId, "single").getState());

        safeStopController.clearGlobalStop();
        register(handler(context -> TaskResult.completed("resumed"), null));
        assertEquals(RunStatus.SUCCEEDED, executionEngine.execute(runId).status());
    }

    @Test
    void highImpactTaskWaitsForHumanApprovalBeforeDispatch() {
        AtomicInteger executions = new AtomicInteger();
        register(handler(context -> {
            executions.incrementAndGet();
            return TaskResult.completed("approved-work");
        }, null));
        UUID runId = createRun(List.of(new TaskDefinition(
                "high-impact",
                TaskKind.CUSTOM,
                Set.of(),
                ImpactLevel.HIGH)));

        ExecutionSummary waiting = executionEngine.execute(runId);

        assertEquals(RunStatus.AWAITING_APPROVAL, waiting.status());
        assertEquals(TaskState.AWAITING_APPROVAL, findTask(runId, "high-impact").getState());
        assertEquals(0, executions.get());
        var request = approvalRepository.findAllByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING)
                .getFirst();

        approvalService.decide(request.getId(), ApprovalDecision.APPROVE, "reviewer", "Change reviewed");
        assertEquals(TaskState.READY, findTask(runId, "high-impact").getState());
        assertEquals(RunStatus.SUCCEEDED, executionEngine.execute(runId).status());
        assertEquals(1, executions.get());
    }

    @Test
    void rejectionCancelsHighImpactTaskAndRun() {
        AtomicInteger executions = new AtomicInteger();
        register(handler(context -> {
            executions.incrementAndGet();
            return TaskResult.completed("must-not-run");
        }, null));
        UUID runId = createRun(List.of(new TaskDefinition(
                "high-impact",
                TaskKind.CUSTOM,
                Set.of(),
                ImpactLevel.HIGH)));
        executionEngine.execute(runId);
        var request = approvalRepository.findAllByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING)
                .getFirst();

        approvalService.decide(request.getId(), ApprovalDecision.REJECT, "reviewer", "Unsafe change");

        assertEquals(RunStatus.CANCELLED, runRepository.findById(runId).orElseThrow().getStatus());
        assertEquals(TaskState.CANCELLED, findTask(runId, "high-impact").getState());
        assertEquals(0, executions.get());
    }

    private UUID createRun(List<TaskDefinition> tasks) {
        return persistenceService.createRun(
                "engine-test",
                "Execution engine integration test",
                dagValidator.validate(tasks));
    }

    private TaskDefinition task(String taskId, Set<String> dependencies) {
        return new TaskDefinition(taskId, TaskKind.CUSTOM, dependencies, ImpactLevel.LOW);
    }

    private TaskExecutionEntity findTask(UUID runId, String taskId) {
        return taskRepository.findByRunIdAndTaskId(runId, taskId).orElseThrow();
    }

    private void register(TaskHandler handler) {
        when(handlerRegistry.handlerFor(handler.kind())).thenReturn(handler);
        when(handlerRegistry.fallbackFor(handler.kind())).thenReturn(Optional.empty());
    }

    private TaskHandler handler(TaskExecution action, Compensation compensation) {
        return new TaskHandler() {
            @Override
            public TaskKind kind() {
                return TaskKind.CUSTOM;
            }

            @Override
            public TaskResult execute(TaskContext context) throws Exception {
                return action.execute(context);
            }

            @Override
            public void compensate(TaskContext context, TaskResult completedResult) throws Exception {
                if (compensation != null) {
                    compensation.compensate(context, completedResult);
                }
            }
        };
    }

    @FunctionalInterface
    private interface TaskExecution {

        TaskResult execute(TaskContext context) throws Exception;
    }

    @FunctionalInterface
    private interface Compensation {

        void compensate(TaskContext context, TaskResult result) throws Exception;
    }
}
