package com.schwab.agentic.orchestration.dag;

import java.util.Set;

public record TaskDefinition(
        String taskId,
        TaskKind kind,
        Set<String> dependencyIds,
        ImpactLevel impactLevel) {

    public TaskDefinition {
        dependencyIds = dependencyIds == null ? Set.of() : Set.copyOf(dependencyIds);
    }
}
