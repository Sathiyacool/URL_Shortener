package com.schwab.agentic.guardrails;

public interface RegisteredCheck {

    RegisteredCheckId id();

    RegisteredCheckResult run() throws Exception;
}
