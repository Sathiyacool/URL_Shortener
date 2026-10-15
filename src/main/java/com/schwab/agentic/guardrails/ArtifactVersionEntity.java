package com.schwab.agentic.guardrails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "artifact_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_artifact_run_path_version",
                columnNames = {"run_id", "artifact_path", "version_number"}),
        indexes = @Index(name = "idx_artifact_run_task", columnList = "run_id, task_key, created_at"))
public class ArtifactVersionEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "task_key", nullable = false, length = 100)
    private String taskId;

    @Column(name = "artifact_path", nullable = false, length = 512)
    private String artifactPath;

    @Column(name = "version_number", nullable = false)
    private int version;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "change_summary", nullable = false, length = 1000)
    private String changeSummary;

    @Column(name = "decision_record_id", nullable = false)
    private UUID decisionRecordId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ArtifactVersionEntity() {
    }

    public ArtifactVersionEntity(
            UUID id,
            UUID runId,
            String taskId,
            String artifactPath,
            int version,
            String sha256,
            String content,
            String changeSummary,
            UUID decisionRecordId,
            Instant createdAt) {
        this.id = id;
        this.runId = runId;
        this.taskId = taskId;
        this.artifactPath = artifactPath;
        this.version = version;
        this.sha256 = sha256;
        this.content = content;
        this.changeSummary = changeSummary;
        this.decisionRecordId = decisionRecordId;
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

    public String getArtifactPath() {
        return artifactPath;
    }

    public int getVersion() {
        return version;
    }

    public String getSha256() {
        return sha256;
    }

    public String getContent() {
        return content;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public UUID getDecisionRecordId() {
        return decisionRecordId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
