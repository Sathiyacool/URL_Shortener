package com.schwab.agentic.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schwab.agentic.orchestration.api.CreateRunRequest;
import com.schwab.agentic.orchestration.api.OrchestrationRunService;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.engine.ExecutionMode;
import com.schwab.agentic.orchestration.engine.ExecutionSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Database-agnostic persistence assertions. The H2 subclass runs on every build; the PostgreSQL subclass
 * runs the identical assertions against a real PostgreSQL container when Docker is available.
 */
abstract class AbstractPersistenceContract {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrchestrationRunService runService;

    @Test
    void flywayAppliedEveryMigrationSuccessfully() {
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"success\" = false", Integer.class);
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"success\" = true", Integer.class);
        assertEquals(0, failed);
        assertTrue(applied >= 1, "At least the initial migration should be applied");
    }

    @Test
    void hibernateSchemaValidationPassedAgainstFlywaySchema() {
        // The application context only starts when ddl-auto=validate accepts the Flyway-created schema.
        assertTrue(jdbcTemplate.queryForObject("select count(*) from short_urls", Integer.class) >= 0);
    }

    @Test
    void runPersistsTaskStatesAndSucceeds() {
        UUID runId = newRun("persist");
        ExecutionSummary summary = runService.execute(runId, ExecutionMode.SEQUENTIAL);
        assertEquals(RunStatus.SUCCEEDED, summary.status());
        assertEquals(RunStatus.SUCCEEDED, runService.get(runId).status());
        assertTrue(runService.verifyAudit(runId).valid());
    }

    @Test
    void concurrentExecutionOfOneRunIsAdmittedExactlyOnce() throws Exception {
        UUID runId = newRun("concurrent");
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ExecutionSummary>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(callers.submit(() -> {
                    start.await();
                    return runService.execute(runId, ExecutionMode.SEQUENTIAL);
                }));
            }
            start.countDown();
            int succeeded = 0;
            int rejected = 0;
            for (Future<ExecutionSummary> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException exception) {
                    assertInstanceOf(IllegalStateException.class, exception.getCause());
                    rejected++;
                }
            }
            assertEquals(1, succeeded);
            assertEquals(1, rejected);
        } finally {
            callers.shutdownNow();
        }
        assertEquals(RunStatus.SUCCEEDED, runService.get(runId).status());
        runService.get(runId).tasks().forEach(task -> assertEquals(1, task.attemptCount(), task.taskId()));
        assertFalse(runService.get(runId).tasks().isEmpty());
    }

    private UUID newRun(String label) {
        return runService.create(new CreateRunRequest(
                "persistence-" + label,
                "Persistence contract run",
                List.of(
                        new TaskDefinition("requirements", TaskKind.REQUIREMENTS, Set.of(), ImpactLevel.LOW),
                        new TaskDefinition("architecture", TaskKind.ARCHITECTURE, Set.of("requirements"), ImpactLevel.LOW))));
    }
}
