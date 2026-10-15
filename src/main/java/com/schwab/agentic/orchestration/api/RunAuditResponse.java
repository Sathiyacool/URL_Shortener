package com.schwab.agentic.orchestration.api;

import java.util.List;
import java.util.UUID;

public record RunAuditResponse(
        UUID runId,
        List<ExecutionEventView> events,
        List<DecisionView> decisions) {

    public RunAuditResponse {
        events = List.copyOf(events);
        decisions = List.copyOf(decisions);
    }
}
