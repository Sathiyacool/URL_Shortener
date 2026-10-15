package com.schwab.agentic.guardrails;

import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuardrailAuditService {

    private final OrchestrationPersistenceService persistenceService;

    public GuardrailAuditService(OrchestrationPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordArtifactDecision(
            UUID runId, String taskId, UUID parentDecisionId, String outcome, String reason) {
        persistenceService.recordDecision(
                runId,
                taskId,
                parentDecisionId,
                DecisionKind.POLICY,
                ActorType.POLICY,
                "guardrails",
                "artifact operation=WRITE_ARTIFACT",
                outcome + ": " + reason);
    }
}
