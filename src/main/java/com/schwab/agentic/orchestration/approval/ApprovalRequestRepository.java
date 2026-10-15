package com.schwab.agentic.orchestration.approval;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, UUID> {

    List<ApprovalRequestEntity> findAllByStatusOrderByRequestedAtAsc(ApprovalStatus status);

    List<ApprovalRequestEntity> findAllByDecidedAtIsNotNull();

    Optional<ApprovalRequestEntity> findFirstByRunIdAndTaskIdOrderByRequestedAtDesc(UUID runId, String taskId);

    boolean existsByRunIdAndStatus(UUID runId, ApprovalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ApprovalRequestEntity request where request.id = :id")
    Optional<ApprovalRequestEntity> findByIdForUpdate(@Param("id") UUID id);
}
