package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.DecisionRecordEntity;
import java.time.Instant;
import java.util.UUID;

public record DecisionView(
        UUID decisionId,
        String taskId,
        UUID parentDecisionId,
        DecisionKind kind,
        ActorType actorType,
        String actorReference,
        String inputSummary,
        String rationale,
        Instant createdAt) {

    public static DecisionView from(DecisionRecordEntity decision) {
        return new DecisionView(
                decision.getId(),
                decision.getTaskId(),
                decision.getParentDecisionId(),
                decision.getKind(),
                decision.getActorType(),
                decision.getActorReference(),
                decision.getInputSummary(),
                decision.getRationale(),
                decision.getCreatedAt());
    }
}
