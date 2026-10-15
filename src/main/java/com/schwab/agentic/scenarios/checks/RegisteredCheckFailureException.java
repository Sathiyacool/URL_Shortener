package com.schwab.agentic.scenarios.checks;

public class RegisteredCheckFailureException extends IllegalStateException {

    public RegisteredCheckFailureException(String message) {
        super(message);
    }
}
