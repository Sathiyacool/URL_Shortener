package com.schwab.agentic.guardrails;

import java.time.Duration;

public record RegisteredCheckResult(boolean passed, String summary, Duration duration) {
}
