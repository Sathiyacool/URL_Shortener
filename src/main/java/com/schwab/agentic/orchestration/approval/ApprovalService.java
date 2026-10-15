package com.schwab.agentic.orchestration.approval;

import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.DecisionRecordEntity;
import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.engine.OrchestrationRecordNotFoundException;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApprovalService.class);
    private static final int MAX_REQUEST_SUMMARY_LENGTH = 1000;

    private final ApprovalRequestRepository approvalRepository;
    private final TaskExecutionRepository taskRepository;
    private final ExecutionRunRepository runRepository;
    private final OrchestrationPersistenceService persistenceService;
    private final Clock clock;

    public ApprovalService(
            ApprovalRequestRepository approvalRepository,
            TaskExecutionRepository taskRepository,
            ExecutionRunRepository runRepository,
            OrchestrationPersistenceService persistenceService,
            Clock clock) {
        this.approvalRepository = approvalRepository;
        this.taskRepository = taskRepository;
        this.runRepository = runRepository;
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    @Transactional
    public ApprovalRequestEntity requestApproval(UUID runId, String taskId, String requestSummary) {
        if (requestSummary == null || requestSummary.isBlank() || requestSummary.length() > MAX_REQUEST_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("Approval summary must contain 1 to 1000 characters");
        }
        TaskExecutionEntity task = taskRepository.findByRunIdAndTaskId(runId, taskId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException(
                        "Task not found in run " + runId + ": " + taskId));
        if (task.getImpactLevel() != ImpactLevel.HIGH) {
            throw new IllegalArgumentException("Human approval requests are reserved for HIGH-impact tasks");
        }

        var previousRequest = approvalRepository.findFirstByRunIdAndTaskIdOrderByRequestedAtDesc(runId, taskId);
        if (previousRequest.isPresent()) {
            ApprovalRequestEntity request = previousRequest.get();
            if (request.getStatus() == ApprovalStatus.PENDING) {
                if (task.getState() == TaskState.PENDING) {
                    persistenceService.transitionTask(
                            runId,
                            taskId,
                            TaskState.AWAITING_APPROVAL,
                            ActorType.POLICY,
                            "approval-gate",
                            "Waiting for human approval");
                }
                ExecutionRunEntity run = loadRun(runId);
                if (run.getStatus() == RunStatus.RUNNING) {
                    persistenceService.transitionRun(
                            runId,
                            RunStatus.AWAITING_APPROVAL,
                            ActorType.POLICY,
                            "approval-gate",
                            "Waiting for human approval");
                }
            }
            return request;
        }

        Instant now = clock.instant();
        DecisionRecordEntity requestDecision = persistenceService.recordDecision(
                runId,
                taskId,
                null,
                DecisionKind.APPROVAL,
                ActorType.POLICY,
                "approval-gate",
                requestSummary,
                "Human approval required before high-impact task execution");
        ApprovalRequestEntity request = approvalRepository.save(new ApprovalRequestEntity(
                UUID.randomUUID(),
                runId,
                taskId,
                task.getImpactLevel(),
                requestSummary,
                requestDecision.getId(),
                now));
        persistenceService.transitionTask(
                runId,
                taskId,
                TaskState.AWAITING_APPROVAL,
                ActorType.POLICY,
                "approval-gate",
                "Waiting for human approval");
        persistenceService.transitionRun(
                runId,
                RunStatus.AWAITING_APPROVAL,
                ActorType.POLICY,
                "approval-gate",
                "Waiting for human approval");
        log.info("Approval requested approvalId={} runId={} task={}", request.getId(), runId, taskId);
        return request;
    }

    @Transactional
    public ApprovalRequestEntity decide(
            UUID approvalId, ApprovalDecision decision, String approver, String rationale) {
        if (decision == null) {
            throw new IllegalArgumentException("Approval decision is required");
        }
        requireText(approver, "Approver", 128);
        requireText(rationale, "Decision rationale", 4000);

        ApprovalRequestEntity request = approvalRepository.findByIdForUpdate(approvalId)
                .orElseThrow(() -> new ApprovalRequestNotFoundException(approvalId));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new ApprovalAlreadyDecidedException(approvalId, request.getStatus());
        }

        ApprovalStatus status = decision == ApprovalDecision.APPROVE
                ? ApprovalStatus.APPROVED
                : ApprovalStatus.REJECTED;
        Instant now = clock.instant();
        request.decide(status, approver, rationale, now);
        approvalRepository.save(request);
        DecisionRecordEntity decisionRecord = persistenceService.recordDecision(
                request.getRunId(),
                request.getTaskId(),
                request.getRequestDecisionId(),
                DecisionKind.APPROVAL,
                ActorType.HUMAN,
                approver,
                request.getRequestSummary(),
                rationale);
        request.setDecisionRecordId(decisionRecord.getId());
        approvalRepository.save(request);

        if (status == ApprovalStatus.APPROVED) {
            persistenceService.transitionTask(
                    request.getRunId(),
                    request.getTaskId(),
                    TaskState.READY,
                    ActorType.HUMAN,
                    approver,
                    "High-impact task approved");
        } else {
            persistenceService.transitionTask(
                    request.getRunId(),
                    request.getTaskId(),
                    TaskState.CANCELLED,
                    ActorType.HUMAN,
                    approver,
                    "High-impact task rejected: " + rationale);
            persistenceService.transitionRun(
                    request.getRunId(),
                    RunStatus.CANCELLED,
                    ActorType.HUMAN,
                    approver,
                    "Run cancelled because a high-impact task was rejected");
        }
        // Approver is caller-supplied, so strip line breaks to prevent log forging.
        log.info("Approval decided approvalId={} runId={} task={} decision={} approver={}",
                approvalId, request.getRunId(), request.getTaskId(), status,
                approver.replaceAll("[\\r\\n]", "_"));
        return request;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestEntity> pendingApprovals() {
        return approvalRepository.findAllByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public boolean hasPendingApproval(UUID runId) {
        return approvalRepository.existsByRunIdAndStatus(runId, ApprovalStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ApprovalRequestEntity> latestForTask(UUID runId, String taskId) {
        return approvalRepository.findFirstByRunIdAndTaskIdOrderByRequestedAtDesc(runId, taskId);
    }

    private ExecutionRunEntity loadRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new OrchestrationRecordNotFoundException("Run not found: " + runId));
    }

    private void requireText(String value, String label, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(label + " must contain 1 to " + maximumLength + " characters");
        }
    }
}
