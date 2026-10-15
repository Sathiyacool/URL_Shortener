package com.schwab.agentic.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "orchestration_decisions",
        indexes = @Index(name = "idx_orchestration_decisions_run_time", columnList = "run_id, created_at"))
public class DecisionRecordEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "task_key", length = 100)
    private String taskId;

    @Column(name = "parent_decision_id")
    private UUID parentDecisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_kind", nullable = false, length = 24)
    private DecisionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private ActorType actorType;

    @Column(name = "actor_reference", length = 128, nullable = false)
    private String actorReference;

    @Column(name = "input_summary", length = 4000)
    private String inputSummary;

    @Column(length = 4000, nullable = false)
    private String rationale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DecisionRecordEntity() {
    }

    public DecisionRecordEntity(
            UUID id,
            UUID runId,
            String taskId,
            UUID parentDecisionId,
            DecisionKind kind,
            ActorType actorType,
            String actorReference,
            String inputSummary,
            String rationale,
            Instant createdAt) {
        this.id = id;
        this.runId = runId;
        this.taskId = taskId;
        this.parentDecisionId = parentDecisionId;
        this.kind = kind;
        this.actorType = actorType;
        this.actorReference = actorReference;
        this.inputSummary = inputSummary;
        this.rationale = rationale;
        this.createdAt = createdAt;
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

    public UUID getParentDecisionId() {
        return parentDecisionId;
    }

    public DecisionKind getKind() {
        return kind;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActorReference() {
        return actorReference;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getRationale() {
        return rationale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
