package com.schwab.agentic.orchestration.domain;

public enum RunStatus {
    CREATED,
    RUNNING,
    AWAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    ROLLING_BACK,
    ROLLED_BACK,
    REPLANNING,
    CANCELLED,
    SAFE_STOPPED
}
