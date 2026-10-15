package com.schwab.agentic.orchestration.repository;

import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import com.schwab.agentic.orchestration.domain.RunStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ExecutionRunRepository extends JpaRepository<ExecutionRunEntity, UUID> {

    List<ExecutionRunEntity> findAllByStatusIn(Collection<RunStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from ExecutionRunEntity run where run.id = :runId")
    java.util.Optional<ExecutionRunEntity> findByIdForUpdate(@Param("runId") UUID runId);
}
