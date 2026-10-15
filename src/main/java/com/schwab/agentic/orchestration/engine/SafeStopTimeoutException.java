package com.schwab.agentic.orchestration.engine;

import java.util.UUID;

public class SafeStopTimeoutException extends RuntimeException {

    public SafeStopTimeoutException(UUID runId) {
        super("Run " + runId + " did not stop all active handlers within the safe-stop timeout");
    }
}
