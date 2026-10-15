package com.schwab.agentic.guardrails;

public class ArtifactApprovalRequiredException extends RuntimeException {

    public ArtifactApprovalRequiredException(String message) {
        super(message);
    }
}
