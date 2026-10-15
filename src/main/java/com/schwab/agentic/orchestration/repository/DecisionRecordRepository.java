package com.schwab.agentic.orchestration.repository;

import com.schwab.agentic.orchestration.domain.DecisionRecordEntity;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecordEntity, UUID> {

    long countByKind(DecisionKind kind);

    List<DecisionRecordEntity> findAllByRunIdOrderByCreatedAtAscIdAsc(UUID runId);

    Optional<DecisionRecordEntity> findFirstByRunIdAndTaskIdOrderByCreatedAtDescIdDesc(UUID runId, String taskId);
}
