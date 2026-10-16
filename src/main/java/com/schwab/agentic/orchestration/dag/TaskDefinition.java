package com.schwab.agentic.orchestration.dag;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

public record TaskDefinition(
        @Schema(description = "Unique task identifier within the run.", example = "validate-expiration")
        String taskId,
        @Schema(description = "Registered task category.", example = "TEST")
        TaskKind kind,
        @Schema(description = "Task identifiers that must succeed before this task is eligible.", example = "[\"requirements\"]")
        Set<String> dependencyIds,
        @Schema(description = "Impact classification; HIGH tasks require explicit approval.", example = "HIGH")
        ImpactLevel impactLevel) {

    public TaskDefinition {
        dependencyIds = dependencyIds == null ? Set.of() : Set.copyOf(dependencyIds);
    }
}
