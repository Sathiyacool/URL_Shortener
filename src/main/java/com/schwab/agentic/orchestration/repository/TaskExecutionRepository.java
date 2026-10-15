package com.schwab.agentic.orchestration.repository;

import com.schwab.agentic.orchestration.domain.TaskExecutionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, UUID> {

    Optional<TaskExecutionEntity> findByRunIdAndTaskId(UUID runId, String taskId);

    List<TaskExecutionEntity> findAllByRunIdOrderByTaskIdAsc(UUID runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from TaskExecutionEntity task where task.runId = :runId order by task.taskId asc")
    List<TaskExecutionEntity> findAllByRunIdForUpdate(@Param("runId") UUID runId);

    boolean existsByRunIdAndTaskId(UUID runId, String taskId);
}
