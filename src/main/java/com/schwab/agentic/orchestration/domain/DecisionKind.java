package com.schwab.agentic.orchestration.domain;

public enum DecisionKind {
    PLAN,
    APPROVAL,
    RETRY,
    FALLBACK,
    REPLAN,
    ROLLBACK,
    POLICY
}
