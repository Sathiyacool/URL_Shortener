package com.schwab.agentic.guardrails;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersionEntity, UUID> {

    Optional<ArtifactVersionEntity> findFirstByRunIdAndArtifactPathOrderByVersionDesc(
            UUID runId, String artifactPath);

    List<ArtifactVersionEntity> findAllByRunIdAndTaskIdOrderByCreatedAtAsc(UUID runId, String taskId);
}
