package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import java.util.Set;

public record TaskView(
        String taskId,
        TaskKind kind,
        ImpactLevel impactLevel,
        TaskState state,
        int attemptCount,
        Set<String> dependencyIds,
        String resultSummary,
        String failureSummary,
        boolean retired) {

    public TaskView {
        dependencyIds = Set.copyOf(dependencyIds);
    }

    public static TaskView from(TaskExecutionEntity task) {
        return new TaskView(
                task.getTaskId(),
                task.getKind(),
                task.getImpactLevel(),
                task.getState(),
                task.getAttemptCount(),
                task.getDependencyIds(),
                task.getResultSummary(),
                task.getFailureSummary(),
                task.isRetired());
    }
}
