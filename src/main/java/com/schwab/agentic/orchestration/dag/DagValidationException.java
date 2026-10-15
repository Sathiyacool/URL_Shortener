package com.schwab.agentic.orchestration.dag;

public class DagValidationException extends IllegalArgumentException {

    public DagValidationException(String message) {
        super(message);
    }
}
