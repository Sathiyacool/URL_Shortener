package com.schwab.agentic.orchestration.domain;

import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "orchestration_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orchestration_task_run_key",
                columnNames = {"run_id", "task_key"}))
public class TaskExecutionEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "task_key", length = 100, nullable = false)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_kind", nullable = false, length = 32)
    private TaskKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", nullable = false, length = 16)
    private ImpactLevel impactLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskState state;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "result_summary", length = 4000)
    private String resultSummary;

    @Column(name = "failure_summary", length = 4000)
    private String failureSummary;

    @Column(nullable = false)
    private boolean retired;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "orchestration_task_dependencies",
            joinColumns = @JoinColumn(name = "task_execution_id"))
    @Column(name = "dependency_task_key", length = 100, nullable = false)
    private Set<String> dependencyIds = new LinkedHashSet<>();

    @Version
    private long version;

    protected TaskExecutionEntity() {
    }

    public TaskExecutionEntity(
            UUID id,
            UUID runId,
            String taskId,
            TaskKind kind,
            ImpactLevel impactLevel,
            Set<String> dependencyIds) {
        this.id = id;
        this.runId = runId;
        this.taskId = taskId;
        this.kind = kind;
        this.impactLevel = impactLevel;
        this.state = TaskState.PENDING;
        this.attemptCount = 0;
        this.dependencyIds = new LinkedHashSet<>(dependencyIds);
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

    public TaskKind getKind() {
        return kind;
    }

    public ImpactLevel getImpactLevel() {
        return impactLevel;
    }

    public TaskState getState() {
        return state;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public String getFailureSummary() {
        return failureSummary;
    }

    public Set<String> getDependencyIds() {
        return Set.copyOf(dependencyIds);
    }

    public boolean isRetired() {
        return retired;
    }

    public void transitionTo(TaskState state) {
        this.state = state;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
        this.failureSummary = null;
    }

    public void setFailureSummary(String failureSummary) {
        this.failureSummary = failureSummary;
    }

    public void updateDefinition(TaskDefinition definition) {
        this.kind = definition.kind();
        this.impactLevel = definition.impactLevel();
        this.dependencyIds.clear();
        this.dependencyIds.addAll(definition.dependencyIds());
        this.retired = false;
    }

    public void retire() {
        this.retired = true;
    }

    public void invalidateForReplan() {
        this.state = TaskState.INVALIDATED;
        resetExecutionResult();
    }

    public void resetForReplan() {
        resetExecutionResult();
    }

    private void resetExecutionResult() {
        this.attemptCount = 0;
        this.resultSummary = null;
        this.failureSummary = null;
    }
}
