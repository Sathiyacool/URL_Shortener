package com.schwab.agentic.scenarios;

import com.schwab.agentic.orchestration.api.OrchestrationRunService;
import com.schwab.agentic.orchestration.approval.ApprovalDecision;
import com.schwab.agentic.orchestration.approval.ApprovalResponse;
import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.engine.ExecutionMode;
import com.schwab.agentic.orchestration.engine.ExecutionSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Drives the human-approval lifecycle of a scenario run through the same services used by the REST API.
 * The decision is always supplied explicitly by an operator; nothing is auto-approved.
 */
@Service
public class ScenarioLifecycleService {

    static final int MAX_APPROVAL_ROUNDS = 5;

    private final OrchestrationRunService runService;
    private final ApprovalService approvalService;

    public ScenarioLifecycleService(OrchestrationRunService runService, ApprovalService approvalService) {
        this.runService = runService;
        this.approvalService = approvalService;
    }

    public record LifecycleOptions(
            ApprovalDecision decision, String approver, String rationale, boolean safeStopBeforeResume) {

        public LifecycleOptions {
            if (decision == null) {
                throw new IllegalArgumentException("An explicit approval decision is required");
            }
            if (approver == null || approver.isBlank()) {
                throw new IllegalArgumentException("An approver identity is required");
            }
            if (rationale == null || rationale.isBlank()) {
                throw new IllegalArgumentException("A decision rationale is required");
            }
        }
    }

    public record LifecycleOutcome(UUID runId, RunStatus finalStatus, List<String> steps) {

        public LifecycleOutcome {
            steps = List.copyOf(steps);
        }
    }

    public LifecycleOutcome drive(ScenarioRunResponse initial, ExecutionMode mode, LifecycleOptions options) {
        List<String> steps = new ArrayList<>();
        UUID runId = initial.runId();
        RunStatus status = initial.status();
        boolean safeStopPending = options.safeStopBeforeResume();
        steps.add("initial status=" + status);

        for (int round = 0; round < MAX_APPROVAL_ROUNDS && status == RunStatus.AWAITING_APPROVAL; round++) {
            List<ApprovalResponse> pending = runService.pendingApprovals(runId);
            if (pending.isEmpty()) {
                break;
            }
            for (ApprovalResponse approval : pending) {
                steps.add("pending approval " + approval.approvalId() + " task=" + approval.taskId());
                var decided = approvalService.decide(
                        approval.approvalId(), options.decision(), options.approver(), options.rationale());
                steps.add("decision " + decided.getStatus() + " by " + decided.getApprover()
                        + " task=" + approval.taskId());
            }
            if (options.decision() == ApprovalDecision.REJECT) {
                status = runService.get(runId).status();
                steps.add("rejection result status=" + status);
                return new LifecycleOutcome(runId, status, steps);
            }
            if (safeStopPending) {
                safeStopPending = false;
                runService.requestSafeStop(runId);
                ExecutionSummary stopped = runService.execute(runId, mode);
                steps.add("safe-stop requested; execute status=" + stopped.status());
                status = stopped.status();
                if (status != RunStatus.SAFE_STOPPED) {
                    continue;
                }
            }
            ExecutionSummary resumed = runService.resume(runId, mode);
            status = resumed.status();
            steps.add("resumed status=" + status);
        }
        return new LifecycleOutcome(runId, status, steps);
    }
}
