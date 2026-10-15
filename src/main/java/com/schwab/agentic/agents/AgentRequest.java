package com.schwab.agentic.agents;

import java.util.Map;
import java.util.UUID;

public record AgentRequest(
        UUID runId,
        String taskId,
        String objective,
        Map<String, String> dependencyOutputs) {

    public AgentRequest {
        if (runId == null || taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Agent run and task identifiers are required");
        }
        objective = objective == null ? "" : objective;
        dependencyOutputs = dependencyOutputs == null ? Map.of() : Map.copyOf(dependencyOutputs);
    }
}
