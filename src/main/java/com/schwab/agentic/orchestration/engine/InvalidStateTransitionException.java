package com.schwab.agentic.orchestration.engine;

public class InvalidStateTransitionException extends IllegalStateException {

    public InvalidStateTransitionException(String entityName, Object from, Object to) {
        super("Invalid " + entityName + " state transition from " + from + " to " + to);
    }
}
