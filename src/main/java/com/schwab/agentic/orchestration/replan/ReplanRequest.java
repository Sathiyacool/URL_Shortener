package com.schwab.agentic.orchestration.replan;

import com.schwab.agentic.orchestration.dag.TaskDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ReplanRequest(
        @Schema(description = "Optional updated run requirements, up to 4000 characters.")
        String updatedInputSummary,
        @Schema(description = "Complete non-empty replacement task DAG.")
        List<TaskDefinition> tasks,
        @Schema(description = "Optional succeeded upstream task whose output is being revised.")
        String changedTaskId,
        @Schema(description = "Replacement output; must be supplied together with changedTaskId.")
        String updatedTaskOutput,
        @Schema(description = "Identity of the person or policy requesting the replan.", example = "operator")
        String requestedBy,
        @Schema(description = "Reason the task graph or input is being revised.", example = "Upstream requirements changed.")
        String rationale) {

    public ReplanRequest {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("A revised DAG must contain at least one task");
        }
        tasks = List.copyOf(tasks);
        if ((changedTaskId == null) != (updatedTaskOutput == null)) {
            throw new IllegalArgumentException("Changed task ID and updated output must be provided together");
        }
        if (changedTaskId != null && changedTaskId.isBlank()) {
            throw new IllegalArgumentException("Changed task ID must not be blank");
        }
        if (requestedBy == null || requestedBy.isBlank() || requestedBy.length() > 128) {
            throw new IllegalArgumentException("Requester must contain 1 to 128 characters");
        }
        if (rationale == null || rationale.isBlank() || rationale.length() > 4000) {
            throw new IllegalArgumentException("Replan rationale must contain 1 to 4000 characters");
        }
        if (updatedInputSummary != null && updatedInputSummary.length() > 4000) {
            throw new IllegalArgumentException("Updated input summary cannot exceed 4000 characters");
        }
        if (updatedTaskOutput != null && updatedTaskOutput.length() > 4000) {
            throw new IllegalArgumentException("Updated task output cannot exceed 4000 characters");
        }
    }
}
