package com.schwab.agentic.orchestration.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schwab.agentic.orchestration.domain.RunStatus;
import org.junit.jupiter.api.Test;

class RunStateMachineTest {

    private final RunStateMachine stateMachine = new RunStateMachine();

    @Test
    void permitsSafeStopAndExplicitResume() {
        assertDoesNotThrow(() -> stateMachine.validate(RunStatus.CREATED, RunStatus.RUNNING));
        assertDoesNotThrow(() -> stateMachine.validate(RunStatus.RUNNING, RunStatus.SAFE_STOPPED));
        assertDoesNotThrow(() -> stateMachine.validate(RunStatus.SAFE_STOPPED, RunStatus.RUNNING));
    }

    @Test
    void rejectsSuccessfulRunRestart() {
        assertThrows(
                InvalidStateTransitionException.class,
                () -> stateMachine.validate(RunStatus.SUCCEEDED, RunStatus.RUNNING));
    }
}
