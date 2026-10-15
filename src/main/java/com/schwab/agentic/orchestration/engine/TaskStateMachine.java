package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.domain.TaskState;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TaskStateMachine {

    private static final Map<TaskState, Set<TaskState>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(TaskState.PENDING, Set.of(
                    TaskState.READY,
                    TaskState.AWAITING_APPROVAL,
                    TaskState.INVALIDATED,
                    TaskState.SKIPPED,
                    TaskState.CANCELLED)),
            Map.entry(TaskState.READY, Set.of(
                    TaskState.RUNNING,
                    TaskState.AWAITING_APPROVAL,
                    TaskState.INVALIDATED,
                    TaskState.FAILED,
                    TaskState.SKIPPED,
                    TaskState.CANCELLED)),
            Map.entry(TaskState.AWAITING_APPROVAL, Set.of(
                    TaskState.READY, TaskState.INVALIDATED, TaskState.SKIPPED, TaskState.CANCELLED)),
            Map.entry(TaskState.RUNNING, Set.of(
                    TaskState.RETRYING,
                    TaskState.SUCCEEDED,
                    TaskState.INVALIDATED,
                    TaskState.FAILED,
                    TaskState.CANCELLED)),
            Map.entry(TaskState.RETRYING, Set.of(
                    TaskState.READY,
                    TaskState.RUNNING,
                    TaskState.INVALIDATED,
                    TaskState.FAILED,
                    TaskState.SKIPPED,
                    TaskState.CANCELLED)),
            Map.entry(TaskState.SUCCEEDED, Set.of(TaskState.ROLLED_BACK, TaskState.INVALIDATED)),
            Map.entry(TaskState.FAILED, Set.of(TaskState.ROLLED_BACK, TaskState.INVALIDATED)),
            Map.entry(TaskState.SKIPPED, Set.of(TaskState.INVALIDATED)),
            Map.entry(TaskState.ROLLED_BACK, Set.of(TaskState.INVALIDATED)),
            Map.entry(TaskState.INVALIDATED, Set.of(TaskState.PENDING, TaskState.SKIPPED)),
            Map.entry(TaskState.CANCELLED, Set.of()));

    public void validate(TaskState from, TaskState to) {
        if (from == null || to == null || !ALLOWED_TRANSITIONS.get(from).contains(to)) {
            throw new InvalidStateTransitionException("task", from, to);
        }
    }
}
