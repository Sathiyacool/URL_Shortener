package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.domain.RunStatus;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RunStateMachine {

    private static final Map<RunStatus, Set<RunStatus>> ALLOWED_TRANSITIONS = Map.of(
            RunStatus.CREATED, Set.of(
                    RunStatus.RUNNING, RunStatus.REPLANNING, RunStatus.CANCELLED, RunStatus.SAFE_STOPPED),
            RunStatus.RUNNING, Set.of(
                    RunStatus.AWAITING_APPROVAL,
                    RunStatus.SUCCEEDED,
                    RunStatus.FAILED,
                    RunStatus.ROLLING_BACK,
                    RunStatus.CANCELLED,
                    RunStatus.SAFE_STOPPED),
            RunStatus.AWAITING_APPROVAL, Set.of(
                    RunStatus.RUNNING,
                    RunStatus.REPLANNING,
                    RunStatus.CANCELLED,
                    RunStatus.SAFE_STOPPED),
            RunStatus.FAILED, Set.of(RunStatus.ROLLING_BACK, RunStatus.REPLANNING, RunStatus.CANCELLED),
            RunStatus.ROLLING_BACK, Set.of(
                    RunStatus.ROLLED_BACK,
                    RunStatus.FAILED,
                    RunStatus.SAFE_STOPPED),
            RunStatus.SAFE_STOPPED, Set.of(RunStatus.RUNNING, RunStatus.REPLANNING, RunStatus.CANCELLED),
            RunStatus.SUCCEEDED, Set.of(RunStatus.REPLANNING),
            RunStatus.ROLLED_BACK, Set.of(RunStatus.REPLANNING),
            RunStatus.REPLANNING, Set.of(RunStatus.CREATED),
            RunStatus.CANCELLED, Set.of());

    public void validate(RunStatus from, RunStatus to) {
        if (from == null || to == null || !ALLOWED_TRANSITIONS.get(from).contains(to)) {
            throw new InvalidStateTransitionException("run", from, to);
        }
    }
}
