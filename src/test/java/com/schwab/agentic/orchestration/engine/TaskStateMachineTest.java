package com.schwab.agentic.orchestration.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schwab.agentic.orchestration.domain.TaskState;
import org.junit.jupiter.api.Test;

class TaskStateMachineTest {

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    @Test
    void permitsNormalExecutionAndRetryTransitions() {
        assertDoesNotThrow(() -> stateMachine.validate(TaskState.PENDING, TaskState.READY));
        assertDoesNotThrow(() -> stateMachine.validate(TaskState.READY, TaskState.RUNNING));
        assertDoesNotThrow(() -> stateMachine.validate(TaskState.RUNNING, TaskState.RETRYING));
        assertDoesNotThrow(() -> stateMachine.validate(TaskState.RETRYING, TaskState.READY));
        assertDoesNotThrow(() -> stateMachine.validate(TaskState.RUNNING, TaskState.SUCCEEDED));
    }

    @Test
    void rejectsSkippingRequiredStates() {
        assertThrows(
                InvalidStateTransitionException.class,
                () -> stateMachine.validate(TaskState.PENDING, TaskState.SUCCEEDED));
    }

    @Test
    void treatsTerminalStatesAsTerminal() {
        assertThrows(
                InvalidStateTransitionException.class,
                () -> stateMachine.validate(TaskState.CANCELLED, TaskState.READY));
    }
}
