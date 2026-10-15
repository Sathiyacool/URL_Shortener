package com.schwab.agentic.orchestration.approval;

import java.util.UUID;

public class ApprovalRequestNotFoundException extends RuntimeException {

    public ApprovalRequestNotFoundException(UUID id) {
        super("Approval request not found: " + id);
    }
}
