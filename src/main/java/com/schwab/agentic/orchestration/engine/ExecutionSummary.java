package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.domain.RunStatus;
import java.util.Map;
import java.util.UUID;

public record ExecutionSummary(UUID runId, RunStatus status, Map<String, Integer> taskCounts, String failureReason) {

    public ExecutionSummary {
        taskCounts = Map.copyOf(taskCounts);
    }
}
