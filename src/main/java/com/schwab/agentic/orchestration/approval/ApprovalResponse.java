package com.schwab.agentic.orchestration.approval;

import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
        UUID approvalId,
        UUID runId,
        String taskId,
        ApprovalStatus status,
        String approver,
        Instant requestedAt,
        Instant decidedAt) {

    public static ApprovalResponse from(ApprovalRequestEntity request) {
        return new ApprovalResponse(
                request.getId(),
                request.getRunId(),
                request.getTaskId(),
                request.getStatus(),
                request.getApprover(),
                request.getRequestedAt(),
                request.getDecidedAt());
    }
}
