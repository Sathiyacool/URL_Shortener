package com.schwab.agentic.guardrails;

public class ArtifactWriteException extends RuntimeException {

    public ArtifactWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
