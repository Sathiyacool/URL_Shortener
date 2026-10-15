package com.schwab.agentic.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orchestration_runs")
public class ExecutionRunEntity {

    public static final String INITIAL_AUDIT_HASH = "0".repeat(64);

    @Id
    private UUID id;

    @Column(name = "scenario_id", length = 64, nullable = false)
    private String scenarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    @Column(name = "input_summary", length = 4000)
    private String inputSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "audit_sequence", nullable = false)
    private long auditSequence;

    @Column(name = "audit_hash", nullable = false, length = 64)
    private String auditHash;

    @Version
    private long version;

    protected ExecutionRunEntity() {
    }

    public ExecutionRunEntity(UUID id, String scenarioId, String inputSummary, Instant createdAt) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.inputSummary = inputSummary;
        this.status = RunStatus.CREATED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.auditSequence = 0;
        this.auditHash = INITIAL_AUDIT_HASH;
    }

    public UUID getId() {
        return id;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getAuditSequence() {
        return auditSequence;
    }

    public String getAuditHash() {
        return auditHash;
    }

    public void updateAuditTip(long sequence, String hash) {
        this.auditSequence = sequence;
        this.auditHash = hash;
    }

    public void transitionTo(RunStatus status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public void updateInputSummary(String inputSummary, Instant updatedAt) {
        this.inputSummary = inputSummary;
        this.updatedAt = updatedAt;
    }
}
