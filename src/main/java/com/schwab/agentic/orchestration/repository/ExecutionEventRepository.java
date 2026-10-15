package com.schwab.agentic.orchestration.repository;

import com.schwab.agentic.orchestration.domain.ExecutionEventEntity;
import com.schwab.agentic.orchestration.domain.ExecutionEventType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionEventRepository extends JpaRepository<ExecutionEventEntity, Long> {

    List<ExecutionEventEntity> findAllByRunIdOrderByOccurredAtAscIdAsc(UUID runId);

    List<ExecutionEventEntity> findAllByRunIdOrderBySequenceNumberAsc(UUID runId);

    java.util.Optional<ExecutionEventEntity> findFirstByRunIdOrderBySequenceNumberDesc(UUID runId);

    List<ExecutionEventEntity> findAllByEventTypeAndNewValueIn(
            ExecutionEventType eventType, Collection<String> newValues);
}
