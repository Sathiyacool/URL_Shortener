package com.schwab.agentic.orchestration.approval;

import java.util.UUID;

public class ApprovalAlreadyDecidedException extends IllegalStateException {

    public ApprovalAlreadyDecidedException(UUID id, ApprovalStatus status) {
        super("Approval request " + id + " is already " + status);
    }
}
