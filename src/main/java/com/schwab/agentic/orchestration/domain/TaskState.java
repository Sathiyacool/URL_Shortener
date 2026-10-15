package com.schwab.agentic.orchestration.domain;

public enum TaskState {
    PENDING,
    READY,
    RUNNING,
    AWAITING_APPROVAL,
    RETRYING,
    SUCCEEDED,
    FAILED,
    ROLLED_BACK,
    INVALIDATED,
    SKIPPED,
    CANCELLED
}
