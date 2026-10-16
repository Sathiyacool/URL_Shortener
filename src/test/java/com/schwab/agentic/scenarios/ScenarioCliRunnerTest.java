package com.schwab.agentic.scenarios;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schwab.agentic.orchestration.approval.ApprovalDecision;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.engine.ExecutionMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

class ScenarioCliRunnerTest {

    private ScenarioService scenarioService;
    private ScenarioLifecycleService lifecycleService;
    private ConfigurableApplicationContext context;
    private ScenarioCliRunner runner;
    private ScenarioRunResponse response;

    @BeforeEach
    void setUp() {
        scenarioService = mock(ScenarioService.class);
        lifecycleService = mock(ScenarioLifecycleService.class);
        context = mock(ConfigurableApplicationContext.class);
        runner = new ScenarioCliRunner(scenarioService, lifecycleService, context);
        response = new ScenarioRunResponse(
                "brownfield", UUID.randomUUID(), RunStatus.SUCCEEDED, Map.of(), null, List.of(), null);
        when(scenarioService.run(any(String.class), any(ExecutionMode.class))).thenReturn(response);
    }

    private void run(String... args) {
        runner.run(new DefaultApplicationArguments(args));
    }

    @Test
    void doesNothingWhenNoScenarioOptionIsSupplied() {
        run("--spring.main.web-application-type=none");
        verify(scenarioService, never()).run(any(String.class), any(ExecutionMode.class));
        verify(context, never()).close();
    }

    @Test
    void defaultsToParallelModeAndKeepsWebContextOpen() {
        run("--scenario=greenfield");
        verify(scenarioService).run("greenfield", ExecutionMode.PARALLEL);
        verify(context, never()).close();
    }

    @Test
    void parsesModeCaseInsensitivelyAndClosesNonWebContext() {
        run("--scenario=greenfield", "--mode=sequential", "--spring.main.web-application-type=none");
        verify(scenarioService).run("greenfield", ExecutionMode.SEQUENTIAL);
        verify(context).close();
    }

    @Test
    void rejectsUnsupportedModeBeforeRunningAnything() {
        assertThrows(IllegalArgumentException.class, () -> run("--scenario=greenfield", "--mode=TURBO"));
        verify(scenarioService, never()).run(any(String.class), any(ExecutionMode.class));
    }

    @Test
    void rejectsBlankScenarioValue() {
        assertThrows(IllegalArgumentException.class, () -> run("--scenario="));
    }

    @Test
    void rejectsUnsupportedDecision() {
        assertThrows(IllegalArgumentException.class,
                () -> run("--scenario=brownfield", "--decision=MAYBE", "--approver=ops"));
        verify(scenarioService, never()).run(any(String.class), any(ExecutionMode.class));
    }

    @Test
    void requiresApproverWhenDecisionIsSupplied() {
        assertThrows(IllegalArgumentException.class, () -> run("--scenario=brownfield", "--decision=APPROVE"));
    }

    @Test
    void safeStopRequiresApproveDecision() {
        assertThrows(IllegalArgumentException.class, () -> run("--scenario=brownfield", "--safe-stop-before-resume"));
        assertThrows(IllegalArgumentException.class, () -> run(
                "--scenario=brownfield", "--decision=REJECT", "--approver=ops", "--safe-stop-before-resume"));
    }

    @Test
    void passesExplicitLifecycleOptionsToSharedLifecycleService() {
        when(lifecycleService.drive(any(), any(), any())).thenReturn(
                new ScenarioLifecycleService.LifecycleOutcome(response.runId(), RunStatus.SUCCEEDED, List.of("ok")));
        assertDoesNotThrow(() -> run("--scenario=brownfield", "--mode=SEQUENTIAL", "--decision=approve",
                "--approver=ops", "--rationale=Reviewed", "--safe-stop-before-resume"));
        ArgumentCaptor<ScenarioLifecycleService.LifecycleOptions> options =
                ArgumentCaptor.forClass(ScenarioLifecycleService.LifecycleOptions.class);
        verify(lifecycleService).drive(eq(response), eq(ExecutionMode.SEQUENTIAL), options.capture());
        assertEquals(ApprovalDecision.APPROVE, options.getValue().decision());
        assertEquals("ops", options.getValue().approver());
        assertEquals("Reviewed", options.getValue().rationale());
        assertEquals(true, options.getValue().safeStopBeforeResume());
    }

    @Test
    void withoutDecisionNeverDrivesLifecycle() {
        run("--scenario=brownfield");
        verify(lifecycleService, never()).drive(any(), any(), any());
    }
}
