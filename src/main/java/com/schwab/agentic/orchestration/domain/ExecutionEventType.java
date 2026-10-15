package com.schwab.agentic.orchestration.domain;

public enum ExecutionEventType {
    RUN_CREATED,
    TASK_CREATED,
    RUN_STATE_CHANGED,
    TASK_STATE_CHANGED,
    DAG_REPLANNED,
    TASK_OUTPUT_REVISED,
    RUN_INPUT_REVISED,
    DECISION_RECORDED
}
