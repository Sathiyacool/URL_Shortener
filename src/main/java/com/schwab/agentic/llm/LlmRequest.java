package com.schwab.agentic.llm;

import com.schwab.agentic.agents.AgentRole;
import java.util.Map;

public record LlmRequest(
        AgentRole role,
        String taskId,
        String instruction,
        String objective,
        Map<String, String> dependencyOutputs) {

    public LlmRequest {
        if (role == null || taskId == null || taskId.isBlank() || instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("LLM request role, task ID, and instruction are required");
        }
        objective = objective == null ? "" : objective;
        dependencyOutputs = dependencyOutputs == null ? Map.of() : Map.copyOf(dependencyOutputs);
    }
}
