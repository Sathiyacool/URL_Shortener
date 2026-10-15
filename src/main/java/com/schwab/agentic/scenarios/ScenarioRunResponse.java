package com.schwab.agentic.scenarios;

import com.schwab.agentic.orchestration.api.RunView;
import com.schwab.agentic.orchestration.approval.ApprovalResponse;
import com.schwab.agentic.orchestration.domain.RunStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScenarioRunResponse(
        String scenarioId,
        UUID runId,
        RunStatus status,
        Map<String, Integer> taskCounts,
        String failureReason,
        List<ApprovalResponse> pendingApprovals,
        RunView run) {

    public ScenarioRunResponse {
        taskCounts = Map.copyOf(taskCounts);
        pendingApprovals = List.copyOf(pendingApprovals);
    }
}
