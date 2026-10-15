package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import com.schwab.agentic.orchestration.domain.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunView(
        UUID runId,
        String scenarioId,
        RunStatus status,
        String inputSummary,
        Instant createdAt,
        Instant updatedAt,
        List<TaskView> tasks) {

    public RunView {
        tasks = List.copyOf(tasks);
    }

    public static RunView from(ExecutionRunEntity run, List<TaskView> tasks) {
        return new RunView(
                run.getId(),
                run.getScenarioId(),
                run.getStatus(),
                run.getInputSummary(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                tasks);
    }
}
