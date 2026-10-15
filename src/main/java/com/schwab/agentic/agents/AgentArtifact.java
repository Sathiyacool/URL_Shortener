package com.schwab.agentic.agents;

public record AgentArtifact(String relativePath, String content) {

    public AgentArtifact {
        if (relativePath == null || relativePath.isBlank() || content == null) {
            throw new IllegalArgumentException("Artifact path and content are required");
        }
    }
}
