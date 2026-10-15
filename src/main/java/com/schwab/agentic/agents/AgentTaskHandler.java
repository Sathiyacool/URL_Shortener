package com.schwab.agentic.agents;

import com.schwab.agentic.guardrails.ArtifactVersionService;
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
import java.util.UUID;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class AgentTaskHandler implements TaskHandler {

    private final TaskKind kind;
    private final Agent agent;
    private final ArtifactVersionService artifactVersionService;
    private final ApprovalService approvalService;
    private final OrchestrationPersistenceService persistenceService;

    public AgentTaskHandler(
            TaskKind kind,
            Agent agent,
            ArtifactVersionService artifactVersionService,
            ApprovalService approvalService,
            OrchestrationPersistenceService persistenceService) {
        if (kind == null || agent == null) {
            throw new IllegalArgumentException("Task kind and agent are required");
        }
        this.kind = kind;
        this.agent = agent;
        this.artifactVersionService = artifactVersionService;
        this.approvalService = approvalService;
        this.persistenceService = persistenceService;
    }

    @Override
    public TaskKind kind() {
        return kind;
    }

    @Override
    public TaskResult execute(TaskContext context) throws Exception {
        context.checkStopRequested();
        Map<String, String> agentContext = new HashMap<>(context.dependencyOutputs());
        if (context.humanApprovalRationale() != null) {
            agentContext.put("human-approval-rationale", context.humanApprovalRationale());
        }
        AgentResult result = agent.execute(new AgentRequest(
                context.runId(),
                context.taskId(),
                context.runInputSummary(),
                agentContext));
        context.checkStopRequested();

        UUID parentDecisionId = approvalService
                .latestForTask(context.runId(), context.taskId())
                .filter(request -> request.getStatus() == ApprovalStatus.APPROVED)
                .map(request -> request.getDecisionRecordId())
                .orElse(null);
        DecisionRecordEntity decision = persistenceService.recordDecision(
                context.runId(),
                context.taskId(),
                parentDecisionId,
                DecisionKind.PLAN,
                ActorType.AGENT,
                agent.role().name().toLowerCase(Locale.ROOT),
                context.runInputSummary(),
                result.summary());

        for (AgentArtifact artifact : result.artifacts()) {
            context.checkStopRequested();
            artifactVersionService.write(
                    context.runId(),
                    context.taskId(),
                    artifact.relativePath(),
                    artifact.content(),
                    decision.getId());
        }
        String summary = "agent=" + agent.role()
                + "; confidence=" + result.confidence()
                + "; artifacts=" + result.artifacts().size()
                + "; " + result.summary();
        return TaskResult.completed(summary);
    }
}
