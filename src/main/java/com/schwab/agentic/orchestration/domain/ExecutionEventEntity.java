package com.schwab.agentic.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "orchestration_events",
        indexes = {
            @Index(name = "idx_orchestration_events_run_time", columnList = "run_id, occurred_at"),
            @Index(name = "idx_orchestration_events_run_sequence", columnList = "run_id, event_sequence")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orchestration_event_run_sequence",
                columnNames = {"run_id", "event_sequence"}))
public class ExecutionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "event_sequence", nullable = false)
    private long sequenceNumber;

    @Column(name = "task_key", length = 100)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ExecutionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private ActorType actorType;

    @Column(name = "actor_reference", length = 128, nullable = false)
    private String actorReference;

    @Column(name = "previous_value", length = 64)
    private String previousValue;

    @Column(name = "new_value", length = 64)
    private String newValue;

    @Column(length = 4000)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    protected ExecutionEventEntity() {
    }

    public ExecutionEventEntity(
            UUID runId,
            long sequenceNumber,
            String taskId,
            ExecutionEventType eventType,
            ActorType actorType,
            String actorReference,
            String previousValue,
            String newValue,
            String details,
            Instant occurredAt,
            String previousHash,
            String eventHash) {
        this.runId = runId;
        this.sequenceNumber = sequenceNumber;
        this.taskId = taskId;
        this.eventType = eventType;
        this.actorType = actorType;
        this.actorReference = actorReference;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.details = details;
        this.occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        this.previousHash = previousHash;
        this.eventHash = eventHash;
    }

    public Long getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getTaskId() {
        return taskId;
    }

    public ExecutionEventType getEventType() {
        return eventType;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActorReference() {
        return actorReference;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getEventHash() {
        return eventHash;
    }

    public void setEventHash(String eventHash) {
        this.eventHash = eventHash;
    }
}
