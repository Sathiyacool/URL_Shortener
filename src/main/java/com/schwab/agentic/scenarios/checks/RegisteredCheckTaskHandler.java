package com.schwab.agentic.scenarios.checks;

import com.schwab.agentic.guardrails.ArtifactOperation;
import com.schwab.agentic.guardrails.GuardrailDecision;
import com.schwab.agentic.guardrails.GuardrailViolationException;
import com.schwab.agentic.guardrails.PolicyEngine;
import com.schwab.agentic.guardrails.RegisteredCheckId;
import com.schwab.agentic.guardrails.RegisteredCheckRegistry;
import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.approval.ApprovalStatus;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.DecisionRecordEntity;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.engine.TaskContext;
import com.schwab.agentic.orchestration.engine.TaskHandler;
import com.schwab.agentic.orchestration.engine.TaskResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RegisteredCheckTaskHandler implements TaskHandler {

    private static final Map<String, RegisteredCheckId> SCENARIO_CHECKS = Map.of(
            "baseline-validation", RegisteredCheckId.SHORTENER_BASELINE,
            "expiration-regression", RegisteredCheckId.DEFAULT_EXPIRATION_REGRESSION);

    private final RegisteredCheckRegistry checkRegistry;
    private final PolicyEngine policyEngine;
    private final ApprovalService approvalService;
    private final OrchestrationPersistenceService persistenceService;

    public RegisteredCheckTaskHandler(
            RegisteredCheckRegistry checkRegistry,
            PolicyEngine policyEngine,
            ApprovalService approvalService,
            OrchestrationPersistenceService persistenceService) {
        this.checkRegistry = checkRegistry;
        this.policyEngine = policyEngine;
        this.approvalService = approvalService;
        this.persistenceService = persistenceService;
    }

    @Override
    public TaskKind kind() {
        return TaskKind.CUSTOM;
    }

    @Override
    public TaskResult execute(TaskContext context) throws Exception {
        context.checkStopRequested();
        RegisteredCheckId checkId = SCENARIO_CHECKS.get(context.taskId());
        if (checkId == null) {
            throw new GuardrailViolationException("No registered scenario check is mapped to this task");
        }
        var policy = policyEngine.evaluate(ArtifactOperation.RUN_REGISTERED_CHECK, null, null, checkId, false);
        if (policy.decision() != GuardrailDecision.ALLOW) {
            throw new GuardrailViolationException("Registered check denied by policy: " + policy.reason());
        }
        var result = checkRegistry.run(checkId);
        UUID parentDecisionId = approvalService
                .latestForTask(context.runId(), context.taskId())
                .filter(request -> request.getStatus() == ApprovalStatus.APPROVED)
                .map(request -> request.getDecisionRecordId())
                .orElse(null);
        DecisionRecordEntity decision = persistenceService.recordDecision(
                context.runId(),
                context.taskId(),
                parentDecisionId,
                DecisionKind.POLICY,
                ActorType.POLICY,
                "registered-checks",
                checkId.name(),
                result.summary());
        String output = "check=" + checkId
                + "; passed=" + result.passed()
                + "; durationMs=" + result.duration().toMillis()
                + "; decision=" + decision.getId()
                + "; " + result.summary();
        if (!result.passed()) {
            throw new RegisteredCheckFailureException(output);
        }
        return TaskResult.completed(output);
    }
}
