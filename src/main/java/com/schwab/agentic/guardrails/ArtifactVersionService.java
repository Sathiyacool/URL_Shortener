package com.schwab.agentic.guardrails;

import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.approval.ApprovalStatus;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.DecisionRecordEntity;
import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.engine.OrchestrationRecordNotFoundException;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtifactVersionService {

    private static final int MAX_ARTIFACT_LENGTH = 256_000;

    private final ArtifactVersionRepository artifactRepository;
    private final TaskExecutionRepository taskRepository;
    private final ApprovalService approvalService;
    private final PolicyEngine policyEngine;
    private final ArtifactSandbox artifactSandbox;
    private final GuardrailAuditService guardrailAuditService;
    private final OrchestrationPersistenceService persistenceService;
    private final Clock clock;

    public ArtifactVersionService(
            ArtifactVersionRepository artifactRepository,
            TaskExecutionRepository taskRepository,
            ApprovalService approvalService,
            PolicyEngine policyEngine,
            ArtifactSandbox artifactSandbox,
            GuardrailAuditService guardrailAuditService,
            OrchestrationPersistenceService persistenceService,
            Clock clock) {
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
        this.approvalService = approvalService;
        this.policyEngine = policyEngine;
        this.artifactSandbox = artifactSandbox;
        this.guardrailAuditService = guardrailAuditService;
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    @Transactional
    public ArtifactVersionEntity write(UUID runId, String taskId, String artifactPath, String content) {
        return writeInternal(runId, taskId, artifactPath, content, null);
    }

    @Transactional
    public ArtifactVersionEntity write(
            UUID runId, String taskId, String artifactPath, String content, UUID parentDecisionId) {
        return writeInternal(runId, taskId, artifactPath, content, parentDecisionId);
    }

    private ArtifactVersionEntity writeInternal(
            UUID runId, String taskId, String artifactPath, String content, UUID parentDecisionId) {
        if (content == null || content.length() > MAX_ARTIFACT_LENGTH) {
            throw new GuardrailViolationException("Artifact content must not exceed 256000 characters");
        }
        TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, taskId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                        "Task not found in run " + runId + ": " + taskId));
        if (task.getState() != TaskState.RUNNING) {
            throw new GuardrailViolationException("Artifacts may only be written by a running task");
        }

        var approval = approvalService.latestForTask(runId, taskId)
                .filter(request -> request.getStatus() == ApprovalStatus.APPROVED);
        GuardrailResult result;
        try {
            result = policyEngine.evaluate(
                    ArtifactOperation.WRITE_ARTIFACT, artifactPath, content, null, approval.isPresent());
        } catch (GuardrailViolationException exception) {
            guardrailAuditService.recordArtifactDecision(
                    runId, taskId, parentDecisionId, "DENIED", exception.getMessage());
            throw exception;
        }
        if (result.decision() == GuardrailDecision.APPROVAL_REQUIRED) {
            guardrailAuditService.recordArtifactDecision(
                    runId, taskId, parentDecisionId, "APPROVAL_REQUIRED", result.reason());
            throw new ArtifactApprovalRequiredException(
                    result.reason() + "; define the task as HIGH impact to create an approval checkpoint");
        }
        if (result.decision() == GuardrailDecision.DENY) {
            guardrailAuditService.recordArtifactDecision(
                    runId, taskId, parentDecisionId, "DENIED", result.reason());
            throw new GuardrailViolationException(result.reason());
        }

        ArtifactVersionEntity previous = artifactRepository
                .findFirstByRunIdAndArtifactPathOrderByVersionDesc(runId, artifactPath)
                .orElse(null);
        if (previous != null && previous.getContent().equals(content)) {
            return previous;
        }
        int version = previous == null ? 1 : previous.getVersion() + 1;
        String hash = sha256(content);
        String summary = previous == null
                ? "Created version 1 (" + content.length() + " characters, sha256 " + hash + ")"
                : "Updated version " + previous.getVersion() + " to " + version + " (sha256 " + hash + ")";
        UUID lineageParent = parentDecisionId != null
                ? parentDecisionId
                : approval.map(request -> request.getDecisionRecordId()).orElse(null);
        DecisionRecordEntity decision = persistenceService.recordDecision(
                runId,
                taskId,
                lineageParent,
                DecisionKind.POLICY,
                ActorType.AGENT,
                "artifact-writer",
                artifactPath,
                summary);

        artifactSandbox.writeVersioned(runId, version, artifactPath, content);
        return artifactRepository.save(new ArtifactVersionEntity(
                UUID.randomUUID(),
                runId,
                taskId,
                artifactPath,
                version,
                hash,
                content,
                summary,
                decision.getId(),
                clock.instant()));
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
