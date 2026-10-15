package com.schwab.agentic.agents;

public class AgentExecutionException extends IllegalStateException {

    public AgentExecutionException(String message) {
        super(message);
    }

    public AgentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
