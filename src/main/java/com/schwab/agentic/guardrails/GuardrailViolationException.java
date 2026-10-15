package com.schwab.agentic.guardrails;

public class GuardrailViolationException extends RuntimeException {

    public GuardrailViolationException(String message) {
        super(message);
    }
}
