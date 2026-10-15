package com.schwab.agentic.agents;

import java.util.List;

public record AgentResult(String summary, double confidence, List<AgentArtifact> artifacts) {

    public AgentResult {
        if (summary == null || summary.isBlank() || summary.length() > 3000) {
            throw new IllegalArgumentException("Agent summary must contain 1 to 3000 characters");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Agent confidence must be between 0 and 1");
        }
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        if (artifacts.stream().anyMatch(artifact -> artifact == null)) {
            throw new IllegalArgumentException("Agent artifacts must not contain null values");
        }
    }
}
