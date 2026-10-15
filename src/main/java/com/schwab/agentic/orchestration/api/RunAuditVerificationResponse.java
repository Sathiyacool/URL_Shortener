package com.schwab.agentic.orchestration.api;

import java.util.UUID;

public record RunAuditVerificationResponse(
        UUID runId,
        boolean valid,
        int eventCount,
        Long firstInvalidSequence,
        String message,
        String finalHash) {
}
