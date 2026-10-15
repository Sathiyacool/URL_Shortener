package com.schwab.agentic.orchestration.engine;

import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public record TaskContext(
        UUID runId,
        String taskId,
        String runInputSummary,
        Map<String, String> dependencyOutputs,
        String humanApprovalRationale,
        BooleanSupplier stopRequested) {

    public TaskContext {
        dependencyOutputs = Map.copyOf(dependencyOutputs);
    }

    public TaskContext(
            UUID runId, String taskId, Map<String, String> dependencyOutputs, BooleanSupplier stopRequested) {
        this(runId, taskId, null, dependencyOutputs, null, stopRequested);
    }

    public TaskContext(
            UUID runId,
            String taskId,
            String runInputSummary,
            Map<String, String> dependencyOutputs,
            BooleanSupplier stopRequested) {
        this(runId, taskId, runInputSummary, dependencyOutputs, null, stopRequested);
    }

    public boolean isStopRequested() {
        return stopRequested.getAsBoolean() || Thread.currentThread().isInterrupted();
    }

    public void checkStopRequested() {
        if (isStopRequested()) {
            throw new TaskStoppedException();
        }
    }
}
