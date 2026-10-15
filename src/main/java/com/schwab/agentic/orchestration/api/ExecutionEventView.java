package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.ExecutionEventEntity;
import com.schwab.agentic.orchestration.domain.ExecutionEventType;
import java.time.Instant;

public record ExecutionEventView(
        Long id,
        long sequenceNumber,
        String taskId,
        ExecutionEventType eventType,
        ActorType actorType,
        String actorReference,
        String previousValue,
        String newValue,
        String details,
        Instant occurredAt,
        String previousHash,
        String eventHash) {

    public static ExecutionEventView from(ExecutionEventEntity event) {
        return new ExecutionEventView(
                event.getId(),
                event.getSequenceNumber(),
                event.getTaskId(),
                event.getEventType(),
                event.getActorType(),
                event.getActorReference(),
                event.getPreviousValue(),
                event.getNewValue(),
                event.getDetails(),
                event.getOccurredAt(),
                event.getPreviousHash(),
                event.getEventHash());
    }
}
