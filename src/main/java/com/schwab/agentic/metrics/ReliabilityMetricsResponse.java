package com.schwab.agentic.metrics;

public record ReliabilityMetricsResponse(
        long totalRuns,
        long completedRuns,
        long successfulRuns,
        long failedRuns,
        double successRate,
        long retryCount,
        long rollbackCount,
        double rollbackFrequency,
        long mttrSampleCount,
        double meanTimeToRecoveryMs,
        double p50EndToEndLatencyMs,
        double p95EndToEndLatencyMs,
        long approvalDecisionCount,
        double meanApprovalWaitMs,
        double p95ApprovalWaitMs) {
}
