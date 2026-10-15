package com.schwab.agentic.orchestration.replan;

import com.schwab.agentic.orchestration.domain.RunStatus;
import java.util.Set;
import java.util.UUID;

public record ReplanResult(
        UUID runId,
        RunStatus status,
        Set<String> invalidatedTaskIds,
        Set<String> preservedTaskIds,
        Set<String> addedTaskIds,
        Set<String> removedTaskIds,
        UUID decisionRecordId) {

    public ReplanResult {
        invalidatedTaskIds = Set.copyOf(invalidatedTaskIds);
        preservedTaskIds = Set.copyOf(preservedTaskIds);
        addedTaskIds = Set.copyOf(addedTaskIds);
        removedTaskIds = Set.copyOf(removedTaskIds);
    }
}
