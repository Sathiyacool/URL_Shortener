package com.schwab.agentic.orchestration.approval;

import com.schwab.agentic.orchestration.dag.ImpactLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "approval_requests",
        indexes = {
            @Index(name = "idx_approval_status_requested", columnList = "status, requested_at"),
            @Index(name = "idx_approval_run_task", columnList = "run_id, task_key, requested_at")
        })
public class ApprovalRequestEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "task_key", length = 100, nullable = false)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 16)
    private ImpactLevel impactLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalStatus status;

    @Column(name = "request_summary", length = 1000, nullable = false)
    private String requestSummary;

    @Column(name = "request_decision_id", nullable = false)
    private UUID requestDecisionId;

    @Column(name = "decision_record_id")
    private UUID decisionRecordId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(length = 128)
    private String approver;

    @Column(name = "decision_rationale", length = 4000)
    private String decisionRationale;

    @Version
    private long version;

    protected ApprovalRequestEntity() {
    }

    public ApprovalRequestEntity(
            UUID id,
            UUID runId,
            String taskId,
            ImpactLevel impactLevel,
            String requestSummary,
            UUID requestDecisionId,
            Instant requestedAt) {
        this.id = id;
        this.runId = runId;
        this.taskId = taskId;
        this.impactLevel = impactLevel;
        this.status = ApprovalStatus.PENDING;
        this.requestSummary = requestSummary;
        this.requestDecisionId = requestDecisionId;
        this.requestedAt = requestedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public String getTaskId() {
        return taskId;
    }

    public ImpactLevel getImpactLevel() {
        return impactLevel;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public String getRequestSummary() {
        return requestSummary;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getApprover() {
        return approver;
    }

    public String getDecisionRationale() {
        return decisionRationale;
    }

    public void decide(ApprovalStatus status, String approver, String rationale, Instant decidedAt) {
        this.status = status;
        this.approver = approver;
        this.decisionRationale = rationale;
        this.decidedAt = decidedAt;
    }

    public UUID getRequestDecisionId() {
        return requestDecisionId;
    }

    public UUID getDecisionRecordId() {
        return decisionRecordId;
    }

    public void setDecisionRecordId(UUID decisionRecordId) {
        this.decisionRecordId = decisionRecordId;
    }
}
