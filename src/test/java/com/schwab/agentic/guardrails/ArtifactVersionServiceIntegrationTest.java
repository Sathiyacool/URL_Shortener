package com.schwab.agentic.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schwab.agentic.orchestration.approval.ApprovalDecision;
import com.schwab.agentic.orchestration.approval.ApprovalRequestEntity;
import com.schwab.agentic.orchestration.approval.ApprovalService;
import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.TaskState;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ArtifactVersionServiceIntegrationTest {

    private static final Path SANDBOX_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "agentic-artifact-tests", UUID.randomUUID().toString());

    @Autowired
    private ArtifactVersionService artifactVersionService;

    @Autowired
    private DagValidator dagValidator;

    @Autowired
    private OrchestrationPersistenceService persistenceService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ArtifactVersionRepository artifactRepository;

    @Autowired
    private DecisionRecordRepository decisionRepository;

    @Autowired
    private ExecutionEventRepository eventRepository;

    @Autowired
    private TaskExecutionRepository taskRepository;

    @Autowired
    private ExecutionRunRepository runRepository;

    @DynamicPropertySource
    static void configureSandbox(DynamicPropertyRegistry registry) {
        registry.add("artifact.sandbox.root", SANDBOX_ROOT::toString);
    }

    @BeforeEach
    void clearDatabase() {
        artifactRepository.deleteAll();
        decisionRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
    }

    @AfterAll
    static void removeSandboxFiles() throws IOException {
        if (Files.exists(SANDBOX_ROOT)) {
            try (var paths = Files.walk(SANDBOX_ROOT)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void createsImmutableVersionsWithHashesAndDecisionLineage() throws IOException {
        UUID runId = createAndStartTask("documentation", ImpactLevel.LOW);

        ArtifactVersionEntity first = artifactVersionService.write(runId, "documentation", "docs\\assessment.md", "first");
        ArtifactVersionEntity second = artifactVersionService.write(runId, "documentation", "docs\\assessment.md", "second");

        assertEquals(1, first.getVersion());
        assertEquals(2, second.getVersion());
        assertNotEquals(first.getSha256(), second.getSha256());
        assertTrue(second.getChangeSummary().contains("Updated version 1 to 2"));
        assertEquals(DecisionKind.POLICY, decisionRepository.findById(second.getDecisionRecordId())
                .orElseThrow()
                .getKind());
        assertEquals("second", Files.readString(
                new ArtifactSandbox(SANDBOX_ROOT.toString())
                        .resolveVersionedPath(runId, 2, "docs\\assessment.md")));
    }

    @Test
    void highImpactArtifactRequiresApprovedHighImpactTask() {
        UUID runId = persistenceService.createRun(
                "guardrail-test",
                "High impact artifact",
                dagValidator.validate(List.of(new TaskDefinition(
                        "pom-change", TaskKind.CODE, Set.of(), ImpactLevel.HIGH))));
        persistenceService.transitionRun(
                runId, RunStatus.RUNNING, ActorType.SYSTEM, "scheduler", "Start run");
        ApprovalRequestEntity request = approvalService.requestApproval(runId, "pom-change", "Modify dependency manifest");
        approvalService.decide(request.getId(), ApprovalDecision.APPROVE, "reviewer", "Reviewed dependency changes");
        persistenceService.transitionRun(
                runId, RunStatus.RUNNING, ActorType.SYSTEM, "scheduler", "Resume approved task");
        persistenceService.transitionTask(
                runId, "pom-change", TaskState.RUNNING, ActorType.SYSTEM, "scheduler", "Task started");

        ArtifactVersionEntity artifact = artifactVersionService.write(runId, "pom-change", "pom.xml", "<project/>");

        assertEquals(request.getId(), approvalService.latestForTask(runId, "pom-change").orElseThrow().getId());
        assertEquals(
                approvalService.latestForTask(runId, "pom-change").orElseThrow().getDecisionRecordId(),
                decisionRepository.findById(artifact.getDecisionRecordId())
                        .orElseThrow()
                        .getParentDecisionId());
    }

    @Test
    void rejectsHighImpactArtifactWhenTaskWasNotApproved() {
        UUID runId = createAndStartTask("configuration", ImpactLevel.MEDIUM);

        assertThrows(
                ArtifactApprovalRequiredException.class,
                () -> artifactVersionService.write(runId, "configuration", "pom.xml", "<project/>"));
        assertTrue(artifactRepository.findAll().isEmpty());
        assertTrue(decisionRepository.findAll().stream().anyMatch(decision ->
                decision.getActorType() == ActorType.POLICY
                        && decision.getRationale().startsWith("APPROVAL_REQUIRED")));
    }

    private UUID createAndStartTask(String taskId, ImpactLevel impact) {
        UUID runId = persistenceService.createRun(
                "guardrail-test",
                "Write artifact",
                dagValidator.validate(List.of(new TaskDefinition(taskId, TaskKind.CODE, Set.of(), impact))));
        persistenceService.transitionRun(runId, RunStatus.RUNNING, ActorType.SYSTEM, "scheduler", "Start test run");
        persistenceService.transitionTask(
                runId, taskId, TaskState.READY, ActorType.SYSTEM, "scheduler", "Dependencies complete");
        persistenceService.transitionTask(
                runId, taskId, TaskState.RUNNING, ActorType.SYSTEM, "scheduler", "Start task");
        return runId;
    }
}
