package com.schwab.agentic.llm;

import com.schwab.agentic.agents.AgentArtifact;
import java.util.List;

public record LlmResponse(String summary, double confidence, List<AgentArtifact> artifacts) {

    public LlmResponse {
        if (summary == null || summary.isBlank() || summary.length() > 3000) {
            throw new IllegalArgumentException("LLM response summary must contain 1 to 3000 characters");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("LLM response confidence must be between 0 and 1");
        }
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        if (artifacts.stream().anyMatch(artifact -> artifact == null)) {
            throw new IllegalArgumentException("LLM response artifacts must not contain null values");
        }
    }
}
